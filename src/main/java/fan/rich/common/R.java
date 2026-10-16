package fan.rich.common;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>字段命名与 layui 表格 / admin-ui-pro 的 view.req() 约定保持一致：
 * <ul>
 *     <li>{@code code}：0 表示成功，非 0 表示失败（对应 response.statusName / statusCode.ok）</li>
 *     <li>{@code msg}：提示信息（对应 response.msgName）</li>
 *     <li>{@code data}：业务数据（对应 response.dataName）</li>
 *     <li>{@code count}：数据总数，layui table 分页需要</li>
 * </ul>
 */
public class R implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：0 成功，非 0 失败。 */
    private int code;

    /** 提示信息。 */
    private String msg;

    /** 业务数据。 */
    private Object data;

    /** 数据总数（layui table 分页使用）。 */
    private long count;

    public R() {
    }

    public R(int code, String msg, Object data, long count) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.count = count;
    }

    /** 成功，不带数据。 */
    public static R ok() {
        return new R(0, "success", null, 0);
    }

    /** 成功，带业务数据。 */
    public static R ok(Object data) {
        return new R(0, "success", data, 0);
    }

    /** 失败。 */
    public static R fail(String msg) {
        return new R(-1, msg, null, 0);
    }

    /** 失败，可指定业务状态码。 */
    public static R fail(int code, String msg) {
        return new R(code, msg, null, 0);
    }

    /** layui table 分页响应。 */
    public static R table(long count, Object data) {
        return new R(0, "", data, count);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
