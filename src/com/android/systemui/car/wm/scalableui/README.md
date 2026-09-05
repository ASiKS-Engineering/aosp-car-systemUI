# Scalable UI Framework

## Overview

This directory houses the Scalable UI framework for Android Automotive SystemUI. It manages the presentation and behavior of UI components like panels and system windows in a flexible and adaptable manner. This framework acts as an abstraction layer within AAOS SysUI, enabling customized experiences through configurable components.

## Core Concepts

### Window State vs. Surface

*   **Window State:** Properties managed by Window Manager (e.g., visibility, size, position, Z-order). Changes are heavyweight and involve core WM services. Examples: launching/closing apps, resizing windows.
*   **Surface:** Lower-level graphical properties (e.g., alpha, scale, translation, crop). Animated efficiently on SurfaceFlinger.
    *   The framework primarily uses `AutoSurfaceTransaction` for batching Surface changes.
    *   Direct `SurfaceControl.Transaction` is used sparingly for features not yet in `AutoSurfaceTransaction`.

## Key Components

*   **`PanelAutoTaskStackTransitionHandlerDelegate`:** Bridge between Window Manager transitions and the Scalable UI.
*   **`PanelTransitionCoordinator`:** Orchestrates panel animations and state changes.
*   **`EventDispatcher`:** Maps system events to panel transactions.
*   **Panels (`TaskPanel`, `DecorPanel`, `SysUIPanel`):** Different types of UI containers.
*   **Configuration (`configuration/`):** Manages UI layout and behavior.
*   **System Events (`systemevents/`):** Handles and dispatches system-level events.
*   **System Windows (`systemwindow/`):** Manages elements like HUN and System Bar.

## Combined Workflow: WM Transitions & System Events

1.  **Initiation:** Triggered by Window Manager (e.g., app launch) or a System Event (e.g., button click).

2.  **WM Transitions Flow:**
    *   WM detects change -> initiates transition.
    *   `PanelAutoTaskStackTransitionHandlerDelegate.handleRequest`:
        *   Calculates `Event` from `TransitionRequestInfo`.
        *   Gets `PanelTransaction` via `EventDispatcher`.
        *   Prepares shell-level `AutoTaskStackTransaction` via `PanelTransitionCoordinator`.
    *   Shell calls `PanelAutoTaskStackTransitionHandlerDelegate.startAnimation`:
        *   Reconciles state and starts Surface-level animations via `PanelTransitionCoordinator`.

3.  **System Events Flow:**
    *   `SystemEventHandler` receives an event -> creates `Event` object.
    *   Gets `PanelTransaction` via `EventDispatcher`.
    *   `PanelTransitionCoordinator.startTransition`:
        *   **If WM change needed:** Initiates shell transition (loops to WM flow).
        *   **If only Surface change:** Plays animations directly (`startDirectAnimation`).

4.  **Animation & State Update:**
    *   `PanelTransitionCoordinator` manages `AnimatorSet`.
    *   Applies `AutoSurfaceTransaction` (or `SurfaceControl.Transaction`) for visual updates.
    *   Finalizes states on completion.

## Interaction: Delegate & Coordinator

*   **Delegate:** Intercepts and *translates* WM transition requests into Scalable UI `Event`s and `PanelTransaction`s.
*   **Coordinator:** *Executes* `PanelTransaction`s from any source (Delegate or System Events), managing animations, Surface updates, and state synchronization.

## Deep Dive: `PanelAutoTaskStackTransitionHandlerDelegate`

*   **Role:** Integrates WM task stack transitions with the panel system.
*   **Responsibilities:**
    *   Intercepts `handleRequest` and `startAnimation` from `AutoTaskStackController`.
    *   Calculates `Event` type (OPEN, CLOSE, HOME) from `TransitionRequestInfo`.
    *   Identifies the target `TaskPanel`.
    *   Uses `PanelTransitionCoordinator` to create `AutoTaskStackTransaction`.
    *   Delegates animation playing to `PanelTransitionCoordinator`.
    *   Reconciles panel states with WM.

## Deep Dive: `PanelTransitionCoordinator`

*   **Role:** Core orchestrator for panel visual transitions and animations.
*   **Responsibilities:**
    *   Manages execution of `PanelTransaction` objects.
    *   Runs animations via `AnimatorSet`.
    *   Differentiates between shell transitions (requiring WM) and direct Surface animations.
    *   Applies Surface updates using `AutoSurfaceTransaction`.
    *   Resolves state conflicts between panels and WM (`reconcileAutoTaskStackState`).
    *   Manages animation lifecycle (stop, merge, complete).
    *   Calculates task focus changes.