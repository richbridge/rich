package fan.rich.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 档案管理模块统计。
 *
 * <p>全部走 JdbcTemplate 聚合，避免把整表数据拉到内存里算。口径要点：
 * <ul>
 *   <li><b>已销毁案卷不参与分布统计</b>，但计入"累计归档"历史量——销毁是档案生命周期的一环，
 *       从门类/年度分布里剔除是为了让分布图反映"现存档案"，而不是被历史卷数冲淡。</li>
 *   <li><b>借阅逾期是动态状态</b>：不靠定时任务，每次看板加载前调 {@link #refreshBorrowStatus()} 重算，
 *       与审计模块的"逾期"口径保持一致。</li>
 *   <li><b>案卷件数 / 页数由卷内文件汇总</b>，新增删除文件后调 {@link #refreshVolumeStats(Long)} 回填，
 *       避免手工录入与明细对不上。</li>
 * </ul>
 */
@Service
public class ArchiveStatsService {

    private final JdbcTemplate jdbc;

    public ArchiveStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ================================================================ 总览

    /** 档案总览 KPI。 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new HashMap<>();
        LocalDate today = LocalDate.now();

        data.put("volumeTotal", num(count("SELECT COUNT(*) FROM arch_volume")));
        data.put("volumeAlive", num(count("SELECT COUNT(*) FROM arch_volume WHERE status <> '已销毁'")));
        data.put("fileTotal", num(sum("SELECT IFNULL(SUM(file_count),0) FROM arch_volume WHERE status <> '已销毁'")));
        data.put("pageTotal", num(sum("SELECT IFNULL(SUM(page_count),0) FROM arch_volume WHERE status <> '已销毁'")));
        data.put("inStock", num(count("SELECT COUNT(*) FROM arch_volume WHERE status = '在库'")));
        data.put("sealed", num(count("SELECT COUNT(*) FROM arch_volume WHERE status = '封存'")));
        data.put("borrowing", num(count("SELECT COUNT(*) FROM arch_borrow WHERE status IN ('借阅中','逾期')")));
        data.put("overdue", num(count("SELECT COUNT(*) FROM arch_borrow WHERE status = '逾期'")));
        data.put("destroyed", num(count("SELECT COUNT(*) FROM arch_volume WHERE status = '已销毁'")));
        data.put("permanent", num(count("SELECT COUNT(*) FROM arch_volume WHERE status <> '已销毁' AND retention = '永久'")));
        data.put("digital", num(count("SELECT COUNT(*) FROM arch_file WHERE carrier = '电子'")));
        data.put("yearArchive", num(count(
                "SELECT COUNT(*) FROM arch_volume WHERE year = ?", today.getYear())));
        data.put("yearBorrow", num(count(
                "SELECT COUNT(*) FROM arch_borrow WHERE YEAR(borrow_date) = ?", today.getYear())));
        data.put("locationTotal", num(count("SELECT COUNT(*) FROM arch_location")));
        data.put("capacity", num(sum("SELECT IFNULL(SUM(capacity),0) FROM arch_location")));
        data.put("used", num(sum("SELECT IFNULL(SUM(used_count),0) FROM arch_location")));
        return data;
    }

    /** 门类分布（卷数 + 页数）。 */
    public List<Map<String, Object>> categoryStats() {
        return jdbc.queryForList("SELECT category AS name, COUNT(*) AS volumeCount,"
                + " IFNULL(SUM(page_count),0) AS pageCount,"
                + " IFNULL(SUM(file_count),0) AS fileCount"
                + " FROM arch_volume WHERE status <> '已销毁' AND category IS NOT NULL AND category <> ''"
                + " GROUP BY category ORDER BY volumeCount DESC, name");
    }

    /** 年度分布（近 8 年）。 */
    public List<Map<String, Object>> yearStats() {
        return jdbc.queryForList("SELECT year AS name, COUNT(*) AS volumeCount,"
                + " IFNULL(SUM(page_count),0) AS pageCount"
                + " FROM arch_volume WHERE status <> '已销毁' AND year IS NOT NULL"
                + " GROUP BY year ORDER BY year");
    }

    /** 保管期限分布。 */
    public List<Map<String, Object>> retentionStats() {
        return jdbc.queryForList("SELECT retention AS name, COUNT(*) AS volumeCount"
                + " FROM arch_volume WHERE status <> '已销毁' AND retention IS NOT NULL AND retention <> ''"
                + " GROUP BY retention ORDER BY volumeCount DESC");
    }

    /** 近 6 个月借阅趋势（借出 / 归还）。 */
    public Map<String, Object> borrowTrend() {
        List<Map<String, Object>> borrow = jdbc.queryForList(
                "SELECT DATE_FORMAT(borrow_date,'%Y-%m') AS month, COUNT(*) AS total"
                        + " FROM arch_borrow WHERE borrow_date IS NOT NULL"
                        + " GROUP BY DATE_FORMAT(borrow_date,'%Y-%m') ORDER BY month");
        Map<String, Object> data = new HashMap<>();
        data.put("borrow", last(borrow, 6));
        List<Map<String, Object>> ret = jdbc.queryForList(
                "SELECT DATE_FORMAT(return_date,'%Y-%m') AS month, COUNT(*) AS total"
                        + " FROM arch_borrow WHERE return_date IS NOT NULL"
                        + " GROUP BY DATE_FORMAT(return_date,'%Y-%m') ORDER BY month");
        data.put("return", last(ret, 6));
        return data;
    }

    /** 提醒：逾期未还、即将到期、待审批销毁、位置将满。 */
    public Map<String, Object> reminders() {
        LocalDate today = LocalDate.now();
        Map<String, Object> data = new HashMap<>();
        data.put("overdue", jdbc.queryForList("SELECT id, borrow_no AS borrowNo, volume_no AS volumeNo,"
                + " volume_title AS volumeTitle, borrower, borrower_dept AS borrowerDept,"
                + " borrow_date AS borrowDate, due_date AS dueDate, overdue_days AS overdueDays"
                + " FROM arch_borrow WHERE status = '逾期' ORDER BY due_date LIMIT 20"));
        data.put("dueSoon", jdbc.queryForList("SELECT id, borrow_no AS borrowNo, volume_no AS volumeNo,"
                + " volume_title AS volumeTitle, borrower, due_date AS dueDate"
                + " FROM arch_borrow WHERE status = '借阅中' AND due_date BETWEEN ? AND ?"
                + " ORDER BY due_date LIMIT 20", today, today.plusDays(7)));
        data.put("destroyPending", jdbc.queryForList("SELECT id, destroy_no AS destroyNo, title,"
                + " category, volume_count AS volumeCount, status, apply_date AS applyDate"
                + " FROM arch_destroy WHERE status IN ('待审批','已审批') ORDER BY apply_date LIMIT 20"));
        List<Map<String, Object>> full = jdbc.queryForList("SELECT location_code AS locationCode,"
                + " office_room AS officeRoom, cabinet_no AS cabinetNo, layer_no AS layerNo,"
                + " folder_no AS folderNo, capacity, used_count AS usedCount"
                + " FROM arch_location WHERE capacity > 0 AND used_count >= capacity * 0.9"
                + " ORDER BY used_count DESC LIMIT 10");
        full.forEach(this::fillLocationPath);
        data.put("locationFull", full);
        return data;
    }

    /** 按办公室汇总存放位置：位置数 / 容量 / 已用卷数 / 使用率。 */
    public List<Map<String, Object>> officeStats() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT office_room AS officeRoom, COUNT(*) AS locationCount,"
                        + " IFNULL(SUM(capacity),0) AS capacity, IFNULL(SUM(used_count),0) AS usedCount,"
                        + " COUNT(DISTINCT cabinet_no) AS cabinetCount"
                        + " FROM arch_location WHERE office_room IS NOT NULL AND office_room <> ''"
                        + " GROUP BY office_room ORDER BY officeRoom");
        rows.forEach(r -> {
            long cap = num(r.get("capacity"));
            long used = num(r.get("usedCount"));
            r.put("rate", cap == 0 ? 0 : Math.round(used * 1000.0 / cap) / 10.0);
        });
        return rows;
    }

    /**
     * 存放位置层级树：办公室 → 柜子 → 层 → 文件夹。
     *
     * <p>返回结构直接供 layui 树形表格使用（{@code name / id / children}），
     * 各层级的容量与卷数为下级汇总，叶子节点带 {@code locationCode} 供案卷定位。
     */
    public List<Map<String, Object>> locationTree() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT location_code AS locationCode, office_room AS officeRoom,"
                        + " cabinet_no AS cabinetNo, layer_no AS layerNo, folder_no AS folderNo,"
                        + " capacity, used_count AS usedCount"
                        + " FROM arch_location ORDER BY office_room, cabinet_no, layer_no, folder_no");
        Map<String, Map<String, Object>> offices = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String office = str(r.get("officeRoom"));
            String cabinet = str(r.get("cabinetNo"));
            String layer = str(r.get("layerNo"));
            String folder = str(r.get("folderNo"));
            long cap = num(r.get("capacity"));
            long used = num(r.get("usedCount"));

            Map<String, Object> officeNode = offices.computeIfAbsent(office,
                    k -> node("OF-" + k, k, "办公室", 0, 0));
            Map<String, Object> cabinetNode = findOrCreate(officeNode,
                    "CB-" + office + "/" + cabinet, cabinet, "柜子");
            Map<String, Object> layerNode = findOrCreate(cabinetNode,
                    "LY-" + office + "/" + cabinet + "/" + layer, layer, "层");

            Map<String, Object> leaf = node("LC-" + r.get("locationCode"), folder, "文件夹", cap, used);
            leaf.put("locationCode", r.get("locationCode"));
            leaf.put("officeRoom", office);
            leaf.put("cabinetNo", cabinet);
            leaf.put("layerNo", layer);
            leaf.put("folderNo", folder);
            children(layerNode).add(leaf);

            add(officeNode, cap, used);
            add(cabinetNode, cap, used);
            add(layerNode, cap, used);
        }
        return new ArrayList<>(offices.values());
    }

    // ================================================================ 树节点工具

    private Map<String, Object> node(String id, String name, String type, long capacity, long used) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("name", name);
        n.put("nodeType", type);
        n.put("capacity", capacity);
        n.put("usedCount", used);
        return n;
    }

    /** 子节点列表按需创建——叶子节点不带 children 字段，layui 树形表格才会渲染成末级。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> children(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.computeIfAbsent("children",
                k -> new ArrayList<Map<String, Object>>());
    }

    /** 在父节点下按 id 找子节点，没有则新建——避免同一层级被重复挂载。 */
    private Map<String, Object> findOrCreate(Map<String, Object> parent,
                                            String id, String name, String type) {
        for (Map<String, Object> c : children(parent)) {
            if (id.equals(c.get("id"))) {
                return c;
            }
        }
        Map<String, Object> created = node(id, name, type, 0, 0);
        children(parent).add(created);
        return created;
    }

    private void add(Map<String, Object> node, long capacity, long used) {
        node.put("capacity", num(node.get("capacity")) + capacity);
        node.put("usedCount", num(node.get("usedCount")) + used);
    }

    /** 拼「办公室 / 柜号 / 层号 / 文件夹号」完整路径，供列表与提醒直接展示。 */
    private void fillLocationPath(Map<String, Object> row) {
        row.put("locationPath", str(row.get("officeRoom")) + " / " + str(row.get("cabinetNo"))
                + " / " + str(row.get("layerNo")) + " / " + str(row.get("folderNo")));
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    // ================================================================ 刷新

    /** 刷新全部派生数据：借阅逾期状态、案卷件数页数、位置已用卷数、销毁清册统计。 */
    public void refreshAll() {
        refreshBorrowStatus();
        refreshVolumeStats(null);
        refreshLocationUsed();
        refreshVolumeLocation();
        jdbc.queryForList("SELECT id FROM arch_destroy").forEach(
                r -> refreshDestroyStats(((Number) r.get("id")).longValue()));
    }

    /**
     * 刷新借阅逾期状态。
     *
     * <p>未归还且应还日期早于今天的置「逾期」并写入逾期天数；
     * 已归还的记录修正为「已归还」，逾期天数按实际归还日算（超期归还仍留痕）。
     */
    public void refreshBorrowStatus() {
        LocalDate today = LocalDate.now();
        jdbc.update("UPDATE arch_borrow SET status = '逾期',"
                + " overdue_days = DATEDIFF(?, due_date), update_time = NOW()"
                + " WHERE return_date IS NULL AND status <> '已丢失' AND due_date < ?", today, today);
        jdbc.update("UPDATE arch_borrow SET status = '借阅中', overdue_days = 0, update_time = NOW()"
                + " WHERE return_date IS NULL AND status = '逾期' AND due_date >= ?", today);
        jdbc.update("UPDATE arch_borrow SET status = '已归还', update_time = NOW()"
                + " WHERE return_date IS NOT NULL AND status <> '已归还' AND status <> '已丢失'");
        jdbc.update("UPDATE arch_borrow SET overdue_days = GREATEST(DATEDIFF(return_date, due_date), 0),"
                + " update_time = NOW() WHERE return_date IS NOT NULL AND due_date IS NOT NULL");
    }

    /** 刷新案卷件数与页数（volumeId 为空则刷新全部）。 */
    public void refreshVolumeStats(Long volumeId) {
        String sql = "UPDATE arch_volume v SET file_count = ("
                + " SELECT IFNULL(COUNT(*),0) FROM arch_file f WHERE f.volume_id = v.id),"
                + " page_count = ("
                + " SELECT IFNULL(SUM(f.page_count),0) FROM arch_file f WHERE f.volume_id = v.id),"
                + " update_time = NOW()";
        if (volumeId == null) {
            jdbc.update(sql);
        } else {
            jdbc.update(sql + " WHERE v.id = ?", volumeId);
        }
    }

    /** 刷新案卷借阅次数，并同步案卷状态（有未归还借阅则为「借阅中」）。 */
    public void refreshVolumeBorrow(Long volumeId) {
        jdbc.update("UPDATE arch_volume v SET borrow_times = ("
                + " SELECT IFNULL(COUNT(*),0) FROM arch_borrow b WHERE b.volume_id = v.id),"
                + " update_time = NOW() WHERE v.id = ?", volumeId);
        boolean borrowing = count("SELECT COUNT(*) FROM arch_borrow WHERE volume_id = ?"
                + " AND status IN ('借阅中','逾期')", volumeId) > 0;
        jdbc.update("UPDATE arch_volume SET status = ?, update_time = NOW()"
                        + " WHERE id = ? AND status IN ('在库','借阅中')",
                borrowing ? "借阅中" : "在库", volumeId);
    }

    /** 刷新各存放位置已用卷数。 */
    public void refreshLocationUsed() {
        jdbc.update("UPDATE arch_location l SET used_count = ("
                + " SELECT IFNULL(COUNT(*),0) FROM arch_volume v"
                + " WHERE v.location_code = l.location_code AND v.status <> '已销毁')");
    }

    /**
     * 按存放位置回刷案卷的位置层级冗余字段。
     *
     * <p>案卷只存一个 {@code location_code}，办公室/柜/层/文件夹是为了列表筛选与检索而冗余的，
     * 位置改名后必须重算，否则筛选结果会与位置档案对不上。
     */
    public void refreshVolumeLocation() {
        jdbc.update("UPDATE arch_volume v JOIN arch_location l ON v.location_code = l.location_code"
                + " SET v.office_room = l.office_room, v.cabinet_no = l.cabinet_no,"
                + " v.layer_no = l.layer_no, v.folder_no = l.folder_no,"
                + " v.location_name = CONCAT_WS(' / ', l.office_room, l.cabinet_no, l.layer_no, l.folder_no),"
                + " v.update_time = NOW()");
    }

    /** 刷新销毁清册的卷数与页数。 */
    public void refreshDestroyStats(Long destroyId) {
        jdbc.update("UPDATE arch_destroy d SET volume_count = ("
                + " SELECT IFNULL(COUNT(*),0) FROM arch_destroy_item i WHERE i.destroy_id = d.id),"
                + " page_count = ("
                + " SELECT IFNULL(SUM(i.page_count),0) FROM arch_destroy_item i WHERE i.destroy_id = d.id),"
                + " update_time = NOW() WHERE d.id = ?", destroyId);
    }

    // ================================================================ 工具

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0L : v;
    }

    private long sum(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0L : v;
    }

    private long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    /** 取最近 n 条（按 month 升序后的尾部）。 */
    private List<Map<String, Object>> last(List<Map<String, Object>> rows, int n) {
        int size = rows.size();
        return size <= n ? rows : new ArrayList<>(rows.subList(size - n, size));
    }
}
