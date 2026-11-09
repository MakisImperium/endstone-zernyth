/**
 * Forms and controls are immutable worker-safe values. Constructors reject nulls and
 * invalid selections, bounds or counts with NullPointerException or IllegalArgumentException.
 * Sending/cancelling requires the server thread, a live plugin and online player;
 * native rejection throws IllegalStateException. Responses complete once on the
 * server thread, including disconnect and plugin disable. Never block on a response.
 * Continuations scheduled onto workers may use response values but cannot call native
 * gameplay APIs. Cancellation detaches this request's callback; it does not globally
 * close the player's UI. Late and foreign responses are ignored. Invalid answers
 * complete with INVALID_RESPONSE rather than executing application selection logic.
 */
package dev.zernyth.api.form;
