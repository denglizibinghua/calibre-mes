package com.bikeassembly.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exhaustive finite-state combinations based on the approved workflow contract. */
class StatusTransitionCoverageTest {
    private static final Set<String> ORDER_EDGES = Set.of(
            "CREATED>RELEASED", "CREATED>CANCELLED", "RELEASED>IN_PROGRESS",
            "RELEASED>CANCELLED", "IN_PROGRESS>PAUSED", "IN_PROGRESS>COMPLETED",
            "PAUSED>IN_PROGRESS", "COMPLETED>CLOSED");

    private static final Set<String> PROCESS_EDGES = Set.of(
            "PENDING>READY", "PENDING>SKIPPED", "PENDING>CANCELLED",
            "READY>IN_PROGRESS", "READY>SKIPPED", "IN_PROGRESS>WAIT_INSPECTION",
            "IN_PROGRESS>COMPLETED", "WAIT_INSPECTION>QUALIFIED",
            "WAIT_INSPECTION>REJECTED", "REJECTED>REWORK", "REWORK>WAIT_INSPECTION",
            "QUALIFIED>COMPLETED");

    private static final Set<String> INSPECTION_EDGES = Set.of(
            "PENDING>ENTERED", "ENTERED>CONFIRMED");

    static Stream<Arguments> orders() {
        return Stream.of(WorkOrderStatus.values()).flatMap(from ->
                Stream.of(WorkOrderStatus.values()).map(to ->
                        Arguments.of(from, to, ORDER_EDGES.contains(from.name() + ">" + to.name()))));
    }

    static Stream<Arguments> processes() {
        return Stream.of(WorkProcessStatus.values()).flatMap(from ->
                Stream.of(WorkProcessStatus.values()).map(to ->
                        Arguments.of(from, to, PROCESS_EDGES.contains(from.name() + ">" + to.name()))));
    }

    static Stream<Arguments> inspections() {
        return Stream.of(InspectionStatus.values()).flatMap(from ->
                Stream.of(InspectionStatus.values()).map(to ->
                        Arguments.of(from, to, INSPECTION_EDGES.contains(from.name() + ">" + to.name()))));
    }

    @ParameterizedTest(name = "order {0} -> {1} = {2}")
    @MethodSource("orders")
    void validatesEveryOrderPair(WorkOrderStatus from, WorkOrderStatus to, boolean expected) {
        assertEquals(expected, StatusTransitionValidator.canTransition(from, to));
    }

    @ParameterizedTest(name = "process {0} -> {1} = {2}")
    @MethodSource("processes")
    void validatesEveryProcessPair(WorkProcessStatus from, WorkProcessStatus to, boolean expected) {
        assertEquals(expected, StatusTransitionValidator.canTransition(from, to));
    }

    @ParameterizedTest(name = "inspection {0} -> {1} = {2}")
    @MethodSource("inspections")
    void validatesEveryInspectionPair(InspectionStatus from, InspectionStatus to, boolean expected) {
        assertEquals(expected, StatusTransitionValidator.canTransition(from, to));
    }

    @Test
    void rejectsNullOrderEndpoints() {
        assertFalse(StatusTransitionValidator.canTransition((WorkOrderStatus) null, WorkOrderStatus.CREATED));
        assertFalse(StatusTransitionValidator.canTransition(WorkOrderStatus.CREATED, null));
        assertFalse(StatusTransitionValidator.canTransition((WorkOrderStatus) null, null));
    }

    @Test
    void rejectsNullProcessEndpoints() {
        assertFalse(StatusTransitionValidator.canTransition((WorkProcessStatus) null, WorkProcessStatus.PENDING));
        assertFalse(StatusTransitionValidator.canTransition(WorkProcessStatus.PENDING, null));
        assertFalse(StatusTransitionValidator.canTransition((WorkProcessStatus) null, null));
    }

    @Test
    void rejectsNullInspectionEndpoints() {
        assertFalse(StatusTransitionValidator.canTransition((InspectionStatus) null, InspectionStatus.PENDING));
        assertFalse(StatusTransitionValidator.canTransition(InspectionStatus.PENDING, null));
        assertFalse(StatusTransitionValidator.canTransition((InspectionStatus) null, null));
    }

    @Test
    void inspectionCodesAndTerminalFlagsMatchContract() {
        for (InspectionStatus status : InspectionStatus.values()) {
            assertEquals(status, InspectionStatus.fromCode(status.getCode()));
            assertEquals(status == InspectionStatus.CONFIRMED, status.isTerminal());
        }
        assertThrows(IllegalArgumentException.class, () -> InspectionStatus.fromCode("invalid"));
        assertThrows(IllegalArgumentException.class, () -> InspectionStatus.fromCode(null));
    }
}
