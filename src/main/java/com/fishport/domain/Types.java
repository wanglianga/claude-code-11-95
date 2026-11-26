package com.fishport.domain;

/** 领域枚举汇总 */
public final class Types {

    private Types() {
    }

    /** 许可申请状态 */
    public enum ApplicationStatus { PENDING, APPROVED, REJECTED }

    /** 许可证状态 */
    public enum PermitStatus { ACTIVE, CLOSED }

    /** 航次状态机：获批 -> 离港 -> 返港 -> 卸货 -> (复称) -> 结案 */
    public enum VoyageState { APPROVED, DEPARTED, RETURNED, UNLOADING, REWEIGHING, CLOSED }

    /** 卸货记录状态 */
    public enum UnloadStatus { DECLARED, WEIGHED, DISPUTED, REWEIGHED, SIGNED }

    /** 额度账流水类型：保留 / 扣减 / 释放 / 调整 */
    public enum LedgerType { RESERVE, DEDUCT, RELEASE, ADJUST }

    /** 预约模式：固定时段 / 滚动叫号 */
    public enum ApptMode { FIXED, ROLLING }

    /** 预约状态 */
    public enum ApptStatus { BOOKED, SCHEDULED, CHECKED_IN, RESCHEDULED, DONE, CANCELLED }

    /** 争议类型：重量争议 / 混装来源不清 / 许可证过期 */
    public enum DisputeType { WEIGHT_DISPUTE, MIXED_LOAD, PERMIT_EXPIRED }

    public enum DisputeStatus { OPEN, RESOLVED }

    /** 通知类型：排定 / 改期 / 队列变化 / 叫号 */
    public enum NotificationType { SCHEDULED, RESCHEDULED, QUEUE_CHANGED, CALLED }

    public enum ReceiptStatus { REPORTED, ACKED }

    /** 航次事件类型（用于历史回放） */
    public enum VoyageEventType {
        PERMIT_ISSUED, DEPART, ARRIVE, UNLOAD_DECLARED, WEIGH_TICKET, REWEIGH_TICKET,
        SIGN, DISPUTE_RAISED, DISPUTE_RESOLVED, NOTE_ADDED, CLOSE
    }

    /** 监管回执事件类型 */
    public enum ReceiptEventType { PERMIT_ISSUED, VOYAGE_DEPARTED, VOYAGE_RETURNED, VOYAGE_CLOSED }
}
