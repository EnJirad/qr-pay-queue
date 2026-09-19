package com.enjirad.qrqueue.domain

/**
 * Which hand the user operates with. Determines where primary actions sit
 * in the thumb zone — right-hand puts them bottom-right, left-hand mirrors
 * to bottom-left. One layout, one parameter, no duplicated UI trees.
 */
enum class HandPreference {
    /** Primary actions anchored to the bottom-right (default). */
    RIGHT,

    /** Primary actions anchored to the bottom-left (mirrored). */
    LEFT;
}
