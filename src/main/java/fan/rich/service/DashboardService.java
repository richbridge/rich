package fan.rich.service;

import fan.rich.dto.NameValue;
import fan.rich.dto.TrendPoint;
import fan.rich.entity.SaleShipment;

import java.util.List;
import java.util.Map;

/**
 * 可视化大屏服务。
 *
 * <p>大屏不再只盯销售台账，而是把公司全部业务模块（销售、价格、盘点、档案、培训、
 * 工资、食堂、审计）的核心指标汇总到一处：既给出经营级的顶层 KPI，也给出各模块的
 * 规模与待办，避免"看销售要开销售看板、看食堂要开食堂看板"的来回切换。
 */
public interface DashboardService {

    /**
     * 综合经营大屏总览：顶层 KPI + 各模块卡片 + 跨模块待办 + 采集状态 + 图表数据。
     *
     * <p>各模块单独取值失败时不会拖垮整屏，对应模块退化为"暂不可用"，
     * 其余模块照常展示——大屏的价值在于一眼看全，某张表缺失不该让整页空白。
     */
    Map<String, Object> overview();

    /**
     * 主力合约收盘价走势（多个金属品种共用一条时间轴）。
     *
     * @param limit 取最近多少个交易日
     */
    Map<String, Object> priceTrend(int limit);

    /** 核心指标汇总。 */
    Map<String, Object> summary();

    /** 月度发货趋势。 */
    List<TrendPoint> trend();

    /** 按发货品种统计金含量。 */
    List<NameValue> category();

    /** 按销售商统计金含量。 */
    List<NameValue> buyer();

    /** 最近发货记录。 */
    List<SaleShipment> recent(int limit);
}
