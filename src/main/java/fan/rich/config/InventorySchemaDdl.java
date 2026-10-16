package fan.rich.config;

/**
 * 盘点模块建表语句。
 *
 * <p>由 {@code InventoryDataInitializer} 在启动时顺序执行，全部使用
 * {@code CREATE TABLE IF NOT EXISTS}，已存在的表不会被重建或清数据。
 *
 * <p>由实体类字段生成，字段类型与实体保持一致；实体新增字段后重新生成本文件即可。
 */
final class InventorySchemaDdl {

    private InventorySchemaDdl() {
    }

    /** 按依赖顺序排列的建表语句。 */
    static final String[] DDL = {
            // Asset
            "CREATE TABLE IF NOT EXISTS ast_asset ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " asset_code VARCHAR(64) COMMENT '资产编号，如 GD-2026-0001。',"
                    + " qr_code VARCHAR(64) COMMENT '二维码码值，全局唯一。',"
                    + " asset_name VARCHAR(128) COMMENT '资产名称。',"
                    + " category VARCHAR(64) COMMENT '资产分类：房屋建筑物/机器设备/运输设备/电子设备/办公家具/其他。',"
                    + " spec VARCHAR(64) COMMENT '规格型号。',"
                    + " unit VARCHAR(64) COMMENT '计量单位：台/套/件/辆/平方米。',"
                    + " quantity DECIMAL(16,3) COMMENT '账面数量。',"
                    + " original_value DECIMAL(16,2) COMMENT '资产原值(元)。',"
                    + " net_value DECIMAL(16,2) COMMENT '资产净值(元)。',"
                    + " buy_date DATE COMMENT '购置日期。',"
                    + " useful_life INT COMMENT '预计使用年限(年)。',"
                    + " use_dept VARCHAR(64) COMMENT '使用部门。',"
                    + " location VARCHAR(64) COMMENT '存放地点。',"
                    + " custodian VARCHAR(64) COMMENT '保管人。',"
                    + " supplier_name VARCHAR(128) COMMENT '供应商。',"
                    + " status VARCHAR(64) COMMENT '状态：在用/闲置/维修/借出/报废。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
            // AstGoods
            "CREATE TABLE IF NOT EXISTS ast_goods ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " goods_code VARCHAR(64) COMMENT '物资编码，如 WZ-01。',"
                    + " qr_code VARCHAR(64) COMMENT '二维码码值，全局唯一。',"
                    + " goods_name VARCHAR(128) COMMENT '物资名称。',"
                    + " category VARCHAR(64) COMMENT '分类：五金材料/劳保用品/办公用品/备品备件/低值易耗/其他。',"
                    + " spec VARCHAR(64) COMMENT '规格型号。',"
                    + " unit VARCHAR(64) COMMENT '计量单位。',"
                    + " warehouse VARCHAR(64) COMMENT '仓库 / 存放地点。',"
                    + " stock_qty DECIMAL(16,3) COMMENT '账面库存数量。',"
                    + " avg_price DECIMAL(14,4) COMMENT '加权平均单价(元)。',"
                    + " total_amount DECIMAL(16,2) COMMENT '库存金额(元)。',"
                    + " safe_stock DECIMAL(16,3) COMMENT '安全库存。',"
                    + " status VARCHAR(64) COMMENT '状态：正常/停用。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
            // InvTask
            "CREATE TABLE IF NOT EXISTS inv_task ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " task_no VARCHAR(64) COMMENT '任务单号，如 PD2026-0001。',"
                    + " task_name VARCHAR(128) COMMENT '任务名称。',"
                    + " task_type VARCHAR(64) COMMENT '盘点类型：固定资产/库存物资/食堂物资/综合盘点。',"
                    + " scope_type VARCHAR(64) COMMENT '范围维度：全部/分类/部门/仓库。',"
                    + " scope_value VARCHAR(64) COMMENT '范围取值，多个用逗号分隔。',"
                    + " check_mode VARCHAR(64) COMMENT '盘点方式：明盘/暗盘。',"
                    + " plan_date DATE COMMENT '计划盘点日期。',"
                    + " deadline DATE COMMENT '截止日期。',"
                    + " owner VARCHAR(64) COMMENT '负责人。',"
                    + " checkers VARCHAR(255) COMMENT '盘点人员，多人逗号分隔。',"
                    + " subject_count INT COMMENT '应盘标的数。',"
                    + " checked_count INT COMMENT '已盘标的数。',"
                    + " normal_count INT COMMENT '正常数。',"
                    + " profit_count INT COMMENT '盘盈数。',"
                    + " loss_count INT COMMENT '盘亏数。',"
                    + " diff_amount DECIMAL(16,2) COMMENT '差异净额(元)，正为盘盈、负为盘亏。',"
                    + " status VARCHAR(64) COMMENT '状态：草稿/进行中/已完成/已取消。',"
                    + " remark VARCHAR(255) COMMENT '备注。',"
                    + " create_by VARCHAR(64) COMMENT '创建人。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
            // InvRecord
            "CREATE TABLE IF NOT EXISTS inv_record ("
                    + " id BIGINT AUTO_INCREMENT COMMENT '主键ID',"
                    + " task_id BIGINT COMMENT '所属任务ID。',"
                    + " task_no VARCHAR(64) COMMENT '任务单号。',"
                    + " subject_type VARCHAR(64) COMMENT '标的类型：ASSET 固定资产 / GOODS 库存物资 / CANTEEN 食堂物资。',"
                    + " subject_id BIGINT COMMENT '标的ID。',"
                    + " subject_code VARCHAR(64) COMMENT '标的编码。',"
                    + " qr_code VARCHAR(64) COMMENT '二维码码值。',"
                    + " subject_name VARCHAR(128) COMMENT '标的名称。',"
                    + " category VARCHAR(64) COMMENT '分类。',"
                    + " spec VARCHAR(64) COMMENT '规格型号。',"
                    + " unit VARCHAR(64) COMMENT '计量单位。',"
                    + " location VARCHAR(64) COMMENT '存放地点 / 仓库。',"
                    + " dept_name VARCHAR(64) COMMENT '归属部门。',"
                    + " custodian VARCHAR(64) COMMENT '保管人。',"
                    + " book_qty DECIMAL(16,3) COMMENT '账面数量（任务创建时冻结）。',"
                    + " actual_qty DECIMAL(16,3) COMMENT '实盘数量。',"
                    + " diff_qty DECIMAL(16,3) COMMENT '差异数量 = 实盘 - 账面。',"
                    + " unit_price DECIMAL(14,4) COMMENT '单价(元)。',"
                    + " diff_amount DECIMAL(16,2) COMMENT '差异金额(元)。',"
                    + " result VARCHAR(64) COMMENT '盘点结果：未盘/正常/盘盈/盘亏。',"
                    + " check_source VARCHAR(64) COMMENT '数据来源：APP扫码/PC扫码/手工录入。',"
                    + " checker VARCHAR(64) COMMENT '盘点人。',"
                    + " check_time DATETIME COMMENT '盘点时间。',"
                    + " handle_status VARCHAR(64) COMMENT '处理状态：待处理/已确认/已调账/无需处理。',"
                    + " handle_opinion VARCHAR(255) COMMENT '处理意见。',"
                    + " handler VARCHAR(64) COMMENT '处理人。',"
                    + " handle_time DATETIME COMMENT '处理时间。',"
                    + " remark VARCHAR(255) COMMENT '盘点备注。',"
                    + " create_time DATETIME COMMENT '创建时间。',"
                    + " update_time DATETIME COMMENT '更新时间。',"
                    + " PRIMARY KEY (id))",
    };
}
