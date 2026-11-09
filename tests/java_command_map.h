#pragma once
#include <sstream>
#include <unordered_map>

#include "endstone/command/command.h"
#include "endstone/command/command_map.h"
namespace endstone {
class GameplayCommands : public CommandMap {
public:
    std::unordered_map<std::string, std::shared_ptr<Command>> entries;
    bool registerCommand(std::shared_ptr<Command> c) override { return entries.emplace(c->getName(), c).second; }
    bool dispatch(CommandSender &sender, std::string line) const override
    {
        std::istringstream in(line);
        std::string name;
        in >> name;
        auto command = getCommand(name);
        if (!command) {
            return false;
        }
        std::vector<std::string> args;
        for (std::string arg; in >> arg;) {
            args.push_back(arg);
        }
        return command->execute(sender, args);
    }
    void clearCommands() override { entries.clear(); }
    std::shared_ptr<Command> getCommand(std::string name) const override
    {
        auto it = entries.find(name);
        return it == entries.end() ? nullptr : it->second;
    }
};

}  // namespace endstone
