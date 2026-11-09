#include <algorithm>
#include <array>
#include <cstdint>
#include <thread>
#include <vector>

#include "endstone/core/plugin/java_handle_ids.h"
int main()
{
    using endstone::core::java::details::allocateId;
    std::atomic<std::int64_t> counter{1};
    std::array<std::vector<std::int64_t>, 4> values;
    std::array<std::thread, 4> workers;
    for (int w = 0; w < 4; ++w) {
        workers[w] = std::thread([&, w] {
            for (int i = 0; i < 1000; ++i) {
                values[w].push_back(allocateId(counter));
            }
        });
    }
    for (auto &worker : workers) {
        worker.join();
    }
    std::vector<std::int64_t> all;
    for (auto &group : values) {
        all.insert(all.end(), group.begin(), group.end());
    }
    std::sort(all.begin(), all.end());
    if (all.size() != 4000 || std::adjacent_find(all.begin(), all.end()) != all.end()) {
        return 1;
    }
    counter = (std::numeric_limits<std::int64_t>::max)();
    for (int i = 0; i < 100; ++i) {
        try {
            allocateId(counter);
            return 2;
        }
        catch (const std::runtime_error &) {
        }
    }
    return counter == (std::numeric_limits<std::int64_t>::max)() ? 0 : 3;
}
