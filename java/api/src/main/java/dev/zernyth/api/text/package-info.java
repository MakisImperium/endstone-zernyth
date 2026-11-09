/**
 * Immutable literal and translation components, safe to construct on Java workers.
 * Sending still requires a live native sender/player on the designated server thread.
 * Translation fallback is explicit: generic CommandSender implementations reject translation
 * components unless they implement native support. Required text values are non-null.
 */
package dev.zernyth.api.text;
