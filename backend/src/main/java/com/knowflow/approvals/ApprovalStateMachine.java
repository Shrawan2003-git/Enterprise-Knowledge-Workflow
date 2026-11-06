package com.knowflow.approvals;

import java.util.Map;
import java.util.Set;

/**
 * Explicit State Machine enforcing valid review lifecycle transitions:
 * DRAFT -> PENDING -> APPROVED | REJECTED | CHANGES_REQUESTED
 * CHANGES_REQUESTED -> PENDING
 */
public class ApprovalStateMachine {

    public enum State {
        DRAFT,
        PENDING,
        APPROVED,
        REJECTED,
        CHANGES_REQUESTED
    }

    private static final Map<State, Set<State>> VALID_TRANSITIONS = Map.of(
            State.DRAFT, Set.of(State.PENDING),
            State.PENDING, Set.of(State.APPROVED, State.REJECTED, State.CHANGES_REQUESTED),
            State.CHANGES_REQUESTED, Set.of(State.PENDING),
            State.APPROVED, Set.of(),
            State.REJECTED, Set.of()
    );

    public static void validateTransition(State from, State to) {
        Set<State> allowed = VALID_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalArgumentException("Invalid state transition from " + from + " to " + to);
        }
    }
}
