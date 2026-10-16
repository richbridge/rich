package fan.rich.config;

/**
 * 档案管理模块建表语句。
 *
 * <p>由 {@code ArchiveDataInitializer} 在启动时顺序执行，全部使用
 * {@code CREATE TABLE IF NOT EXISTS}，已存在的表不会被重建或清数据。
 *
 * <p>由实体类字段生成（列名 snake_case，与 MyBatis-Plus 默认驼峰↔下划线映射一致），
 * 实体新增字段后重新生成本文件即可。
 */
final class ArchiveSchemaDdl {

    private ArchiveSchemaDdl() {
    }

    /** 按依赖顺序排列的建表语句。 */
    static final String[] DDL = {
            // ArchCategory
            "CREATE TABLE IF NOT EXISTS arch_category ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " category_code VARCHAR(64) COMMENT '门类代码，如 WS / KJ / RS。',"
                    + " category_name VARCHAR(128) COMMENT '门类名称，如 文书档案。',"
                    + " keeper_dept VARCHAR(64) COMMENT '归口管理部门。',"
                    + " retention VARCHAR(32) COMMENT '默认保管期限：永久 / 30年 / 10年 / 5年。',"
                    + " seq INT COMMENT '排序号，越小越靠前。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
            // ArchLocation
            "CREATE TABLE IF NOT EXISTS arch_location ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " location_code VARCHAR(64) COMMENT '位置编号，如 A-01-1-03。',"
                    + " office_room VARCHAR(128) COMMENT '办公室 / 档案室名称。',"
                    + " room_name VARCHAR(128) COMMENT '库房名称（兼容字段，与办公室一致）。',"
                    + " cabinet_no VARCHAR(32) COMMENT '柜号。',"
                    + " layer_no VARCHAR(32) COMMENT '层（节）号。',"
                    + " folder_no VARCHAR(64) COMMENT '文件夹 / 档案盒号。',"
                    + " capacity INT COMMENT '容量（卷）。',"
                    + " used_count INT COMMENT '已存卷数，由案卷统计回填。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
            // ArchVolume
            "CREATE TABLE IF NOT EXISTS arch_volume ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " volume_no VARCHAR(64) COMMENT '案卷号，如 2026-WS-0001。',"
                    + " title VARCHAR(255) COMMENT '案卷题名。',"
                    + " category VARCHAR(64) COMMENT '档案门类。',"
                    + " year INT COMMENT '所属年度。',"
                    + " retention VARCHAR(32) COMMENT '保管期限：永久 / 30年 / 10年 / 5年。',"
                    + " security VARCHAR(32) COMMENT '密级：公开 / 内部 / 秘密 / 机密。',"
                    + " start_date DATE COMMENT '卷内文件起始日期。',"
                    + " end_date DATE COMMENT '卷内文件截止日期。',"
                    + " file_count INT COMMENT '卷内文件件数，由明细汇总回填。',"
                    + " page_count INT COMMENT '总页数，由明细汇总回填。',"
                    + " carrier VARCHAR(32) COMMENT '载体：纸质 / 电子 / 声像 / 实物。',"
                    + " archive_dept VARCHAR(64) COMMENT '归档部门。',"
                    + " archivist VARCHAR(64) COMMENT '立卷人 / 归档人。',"
                    + " archive_date DATE COMMENT '归档日期。',"
                    + " location_code VARCHAR(64) COMMENT '存放位置编号。',"
                    + " location_name VARCHAR(128) COMMENT '存放位置完整路径（冗余文本）。',"
                    + " office_room VARCHAR(128) COMMENT '存放办公室（冗余）。',"
                    + " cabinet_no VARCHAR(32) COMMENT '存放柜号（冗余）。',"
                    + " layer_no VARCHAR(32) COMMENT '存放层号（冗余）。',"
                    + " folder_no VARCHAR(64) COMMENT '存放文件夹号（冗余）。',"
                    + " box_no VARCHAR(32) COMMENT '盒号。',"
                    + " qr_code VARCHAR(64) COMMENT '二维码码值，供扫码定位案卷。',"
                    + " borrow_times INT COMMENT '累计借阅次数。',"
                    + " status VARCHAR(32) COMMENT '状态：在库 / 借阅中 / 封存 / 已销毁。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
            // ArchFile
            "CREATE TABLE IF NOT EXISTS arch_file ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " volume_id BIGINT COMMENT '所属案卷ID。',"
                    + " volume_no VARCHAR(64) COMMENT '所属案卷号（冗余，便于检索）。',"
                    + " item_no INT COMMENT '件号（卷内顺序号）。',"
                    + " doc_no VARCHAR(64) COMMENT '文号。',"
                    + " title VARCHAR(255) COMMENT '文件题名。',"
                    + " responsible VARCHAR(64) COMMENT '责任者（发文单位 / 作者）。',"
                    + " doc_date DATE COMMENT '成文日期。',"
                    + " page_count INT COMMENT '页数。',"
                    + " page_no VARCHAR(64) COMMENT '页号，如 1-12。',"
                    + " security VARCHAR(32) COMMENT '密级：公开 / 内部 / 秘密 / 机密。',"
                    + " carrier VARCHAR(32) COMMENT '载体：纸质 / 电子 / 声像。',"
                    + " file_path VARCHAR(255) COMMENT '电子文件路径 / 扫描件。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
            // ArchBorrow
            "CREATE TABLE IF NOT EXISTS arch_borrow ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " borrow_no VARCHAR(64) COMMENT '借阅单号，如 JY2026-0001。',"
                    + " volume_id BIGINT COMMENT '案卷ID。',"
                    + " volume_no VARCHAR(64) COMMENT '案卷号（冗余）。',"
                    + " volume_title VARCHAR(255) COMMENT '案卷题名（冗余）。',"
                    + " borrower VARCHAR(64) COMMENT '借阅人。',"
                    + " borrower_dept VARCHAR(64) COMMENT '借阅人部门。',"
                    + " purpose VARCHAR(255) COMMENT '借阅用途。',"
                    + " borrow_date DATE COMMENT '借出日期。',"
                    + " due_date DATE COMMENT '应还日期。',"
                    + " return_date DATE COMMENT '实际归还日期。',"
                    + " handler VARCHAR(64) COMMENT '经手人（档案管理员）。',"
                    + " approver VARCHAR(64) COMMENT '审批人。',"
                    + " overdue_days INT COMMENT '逾期天数，归还或刷新时计算。',"
                    + " status VARCHAR(32) COMMENT '状态：借阅中 / 已归还 / 逾期 / 已丢失。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
            // ArchDestroy
            "CREATE TABLE IF NOT EXISTS arch_destroy ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " destroy_no VARCHAR(64) COMMENT '销毁单号，如 XH2026-0001。',"
                    + " title VARCHAR(255) COMMENT '清册题名。',"
                    + " category VARCHAR(64) COMMENT '档案门类。',"
                    + " volume_count INT COMMENT '销毁卷数，由明细汇总回填。',"
                    + " page_count INT COMMENT '销毁页数，由明细汇总回填。',"
                    + " reason VARCHAR(255) COMMENT '销毁原因。',"
                    + " apply_dept VARCHAR(64) COMMENT '申请部门。',"
                    + " applicant VARCHAR(64) COMMENT '申请人。',"
                    + " apply_date DATE COMMENT '申请日期。',"
                    + " approver VARCHAR(64) COMMENT '审批人。',"
                    + " approve_date DATE COMMENT '审批日期。',"
                    + " witness VARCHAR(64) COMMENT '监销人。',"
                    + " method VARCHAR(32) COMMENT '销毁方式：粉碎 / 焚烧 / 电子数据删除。',"
                    + " destroy_date DATE COMMENT '销毁日期。',"
                    + " status VARCHAR(32) COMMENT '状态：草稿 / 待审批 / 已审批 / 已销毁。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
            // ArchDestroyItem
            "CREATE TABLE IF NOT EXISTS arch_destroy_item ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " destroy_id BIGINT COMMENT '所属销毁清册ID。',"
                    + " volume_id BIGINT COMMENT '案卷ID。',"
                    + " volume_no VARCHAR(64) COMMENT '案卷号。',"
                    + " volume_title VARCHAR(255) COMMENT '案卷题名。',"
                    + " category VARCHAR(64) COMMENT '档案门类。',"
                    + " retention VARCHAR(32) COMMENT '保管期限。',"
                    + " page_count INT COMMENT '页数。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
    };

    /**
     * 增量列升级：{表名, 列名, 列定义}。
     *
     * <p>MySQL 5.7 的 {@code ALTER TABLE ADD COLUMN} 不支持 {@code IF NOT EXISTS}，
     * 由初始化器查 {@code information_schema} 判断后再补列，因此已存在的旧表也能平滑升级。
     */
    static final String[][] ENSURE_COLUMNS = {
            {"arch_location", "office_room",
                    "VARCHAR(128) COMMENT '办公室 / 档案室名称。'"},
            {"arch_location", "folder_no",
                    "VARCHAR(64) COMMENT '文件夹 / 档案盒号。'"},
            {"arch_volume", "office_room",
                    "VARCHAR(128) COMMENT '存放办公室（冗余）。'"},
            {"arch_volume", "cabinet_no",
                    "VARCHAR(32) COMMENT '存放柜号（冗余）。'"},
            {"arch_volume", "layer_no",
                    "VARCHAR(32) COMMENT '存放层号（冗余）。'"},
            {"arch_volume", "folder_no",
                    "VARCHAR(64) COMMENT '存放文件夹号（冗余）。'"},
    };
}
