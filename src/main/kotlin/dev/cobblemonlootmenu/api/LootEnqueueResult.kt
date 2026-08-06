package dev.cobblemonlootmenu.api

enum class LootEnqueueResult {
    ENQUEUED,
    EMPTY,
    QUEUE_FULL_DROPPED,
    STACK_LIMIT_DROPPED
}
