#pragma once
#include <memory>

#include "endstone/command/command_map.h"
#include "endstone/plugin/plugin_loader.h"

namespace endstone::core {
// Uses the standard manager for ordering, injection, enable/disable and resource cleanup.
class JavaPluginLoader final : public PluginLoader {
public:
    explicit JavaPluginLoader(Server &server, CommandMap *commands = nullptr,
                              std::function<void(const std::shared_ptr<Command> &)> remove = {});
    ~JavaPluginLoader() override;
    Plugin *loadPlugin(std::string file) override;
    std::vector<std::string> getPluginFileFilters() const override { return {"\\.jar$"}; }

private:
    std::vector<std::unique_ptr<Plugin>> plugins_;
    CommandMap *commands_;
    std::function<void(const std::shared_ptr<Command> &)> remove_;
};
}  // namespace endstone::core
