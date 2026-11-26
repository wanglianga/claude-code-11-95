package com.fishport.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 请求 DTO 汇总 */
public final class Requests {

    private Requests() {
    }

    /** 许可申请：船号、船长与船员、计划海区、作业时间、渔具、目标物种及申报捕捞上限 */
    public record ApplyPermitRequest(
            @NotBlank String vesselNo,
            @NotBlank String captainName,
            @Positive int crewCount,
            String crewList,
            @NotBlank String areaCode,
            @NotNull LocalDateTime plannedStart,
            @NotNull LocalDateTime plannedEnd,
            @NotBlank String gearType,
            @NotNull List<Item> items) {
        public record Item(@NotBlank String speciesCode, @NotNull @Positive BigDecimal declaredLimitKg) {
        }
    }

    public record DeclareUnloadRequest(@NotBlank String speciesCode, @NotNull BigDecimal declaredKg) {
    }

    public record WeighTicketRequest(
            @NotBlank String ticketNo,
            @NotBlank String voyageNo,
            @NotBlank String speciesCode,
            @NotNull BigDecimal grossKg,
            @NotNull BigDecimal tareKg,
            LocalDateTime weighedAt) {
    }

    public record DisputeRequest(Long unloadRecordId, @NotBlank String type, String description) {
    }

    public record ResolveRequest(@NotBlank String resolution) {
    }

    public record NoteRequest(@NotBlank String content) {
    }

    public record BookAppointmentRequest(
            @NotBlank String voyageNo,
            @NotBlank String portCode,
            @NotBlank String mode,
            LocalDateTime requestedStart,
            LocalDateTime plannedArrival,
            @Positive int durationMin,
            @NotNull LocalDateTime latestFinish,
            BigDecimal expectedUnloadKg) {
    }

    public record ComputeRequest(@NotBlank String portCode, @NotNull LocalDate date, @NotBlank String mode) {
    }

    public record CheckinRequest(@NotNull LocalDateTime actualArrival) {
    }

    /** 模拟船舶终端事件 */
    public record TerminalEventRequest(@NotBlank String voyageNo, @NotBlank String eventType, String eventId) {
    }
}
