#pragma once
#include <atomic>
#include <limits>
#include <stdexcept>
namespace endstone::core::java::details {
// Saturates at exhaustion: no wrapping, recycling or ABA after overflow.
template <class T>
T allocateId(std::atomic<T> &counter)
{
    auto value = counter.load();
    do {
        if (value <= 0 || value == (std::numeric_limits<T>::max)()) {
            throw std::runtime_error("Gameplay handle space exhausted");
        }
    } while (!counter.compare_exchange_weak(value, value + 1));
    return value;
}
}  // namespace endstone::core::java::details
