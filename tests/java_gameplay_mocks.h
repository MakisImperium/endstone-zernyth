#pragma once
// Test doubles for the existing public Endstone contracts. No native BDS objects.
#include <gmock/gmock.h>

#include "endstone/block/block.h"
#include "endstone/command/console_command_sender.h"
#include "endstone/level/dimension.h"
#include "endstone/level/level.h"
#include "endstone/player.h"
namespace endstone {
class GameplayMockConsole : public ConsoleCommandSender {
public:
    MOCK_METHOD((PermissionLevel), getPermissionLevel, (), (const, override));
    MOCK_METHOD((bool), isPermissionSet, (std::string), (const, override));
    MOCK_METHOD((bool), isPermissionSet, (const Permission &), (const, override));
    MOCK_METHOD((bool), hasPermission, (std::string), (const, override));
    MOCK_METHOD((bool), hasPermission, (const Permission &), (const, override));
    MOCK_METHOD((PermissionAttachment *), addAttachment, (Plugin &, const std::string &, bool), (override));
    MOCK_METHOD((PermissionAttachment *), addAttachment, (Plugin &), (override));
    MOCK_METHOD((bool), removeAttachment, (PermissionAttachment &), (override));
    MOCK_METHOD((void), recalculatePermissions, (), (override));
    MOCK_METHOD((std::unordered_set<PermissionAttachmentInfo *>), getEffectivePermissions, (), (const, override));
    MOCK_METHOD((CommandSender *), asCommandSender, (), (const, override));
    MOCK_METHOD((ConsoleCommandSender *), asConsole, (), (const, override));
    MOCK_METHOD((BlockCommandSender *), asBlock, (), (const, override));
    MOCK_METHOD((Actor *), asActor, (), (const, override));
    MOCK_METHOD((Player *), asPlayer, (), (const, override));
    MOCK_METHOD((void), sendMessage, (const Message &), (const, override));
    MOCK_METHOD((void), sendErrorMessage, (const Message &), (const, override));
    MOCK_METHOD((Server &), getServer, (), (const, override));
    MOCK_METHOD((std::string), getName, (), (const, override));
};

class GameplayMockPlayer : public Player {
public:
    MOCK_METHOD((PermissionLevel), getPermissionLevel, (), (const, override));
    MOCK_METHOD((bool), isPermissionSet, (std::string), (const, override));
    MOCK_METHOD((bool), isPermissionSet, (const Permission &), (const, override));
    MOCK_METHOD((bool), hasPermission, (std::string), (const, override));
    MOCK_METHOD((bool), hasPermission, (const Permission &), (const, override));
    MOCK_METHOD((PermissionAttachment *), addAttachment, (Plugin &, const std::string &, bool), (override));
    MOCK_METHOD((PermissionAttachment *), addAttachment, (Plugin &), (override));
    MOCK_METHOD((bool), removeAttachment, (PermissionAttachment &), (override));
    MOCK_METHOD((void), recalculatePermissions, (), (override));
    MOCK_METHOD((std::unordered_set<PermissionAttachmentInfo *>), getEffectivePermissions, (), (const, override));
    MOCK_METHOD((CommandSender *), asCommandSender, (), (const, override));
    MOCK_METHOD((ConsoleCommandSender *), asConsole, (), (const, override));
    MOCK_METHOD((BlockCommandSender *), asBlock, (), (const, override));
    MOCK_METHOD((Actor *), asActor, (), (const, override));
    MOCK_METHOD((Player *), asPlayer, (), (const, override));
    MOCK_METHOD((void), sendMessage, (const Message &), (const, override));
    MOCK_METHOD((void), sendErrorMessage, (const Message &), (const, override));
    MOCK_METHOD((Server &), getServer, (), (const, override));
    MOCK_METHOD((std::string), getName, (), (const, override));
    MOCK_METHOD((Mob *), asMob, (), (const, override));
    MOCK_METHOD((Item *), asItem, (), (const, override));
    MOCK_METHOD((std::string), getType, (), (const, override));
    MOCK_METHOD((std::uint64_t), getRuntimeId, (), (const, override));
    MOCK_METHOD((Location), getLocation, (), (const, override));
    MOCK_METHOD((Vector), getVelocity, (), (const, override));
    MOCK_METHOD((bool), isOnGround, (), (const, override));
    MOCK_METHOD((bool), isInWater, (), (const, override));
    MOCK_METHOD((bool), isInLava, (), (const, override));
    MOCK_METHOD((Level &), getLevel, (), (const, override));
    MOCK_METHOD((Dimension &), getDimension, (), (const, override));
    MOCK_METHOD((void), setRotation, (float, float), (override));
    MOCK_METHOD((bool), teleport, (const Location &), (override));
    MOCK_METHOD((bool), teleport, (const Actor &), (override));
    MOCK_METHOD((std::int64_t), getId, (), (const, override));
    MOCK_METHOD((void), remove, (), (override));
    MOCK_METHOD((bool), isDead, (), (const, override));
    MOCK_METHOD((bool), isValid, (), (const, override));
    MOCK_METHOD((std::vector<std::string>), getScoreboardTags, (), (const, override));
    MOCK_METHOD((bool), addScoreboardTag, (std::string), (const, override));
    MOCK_METHOD((bool), removeScoreboardTag, (std::string), (const, override));
    MOCK_METHOD((bool), isNameTagVisible, (), (const, override));
    MOCK_METHOD((void), setNameTagVisible, (bool), (override));
    MOCK_METHOD((bool), isNameTagAlwaysVisible, (), (const, override));
    MOCK_METHOD((void), setNameTagAlwaysVisible, (bool), (override));
    MOCK_METHOD((std::string), getNameTag, (), (const, override));
    MOCK_METHOD((void), setNameTag, (std::string), (override));
    MOCK_METHOD((std::string), getScoreTag, (), (const, override));
    MOCK_METHOD((void), setScoreTag, (std::string), (override));
    MOCK_METHOD((bool), isGliding, (), (const, override));
    MOCK_METHOD((int), getHealth, (), (const, override));
    MOCK_METHOD((void), setHealth, (int), (const, override));
    MOCK_METHOD((int), getMaxHealth, (), (const, override));
    MOCK_METHOD((void), setMaxHealth, (int), (const, override));
    MOCK_METHOD((UUID), getUniqueId, (), (const, override));
    MOCK_METHOD((bool), isOp, (), (const, override));
    MOCK_METHOD((void), setOp, (bool), (override));
    MOCK_METHOD((std::string), getXuid, (), (const, override));
    MOCK_METHOD((SocketAddress), getAddress, (), (const, override));
    MOCK_METHOD((void), transfer, (std::string, int), (const, override));
    MOCK_METHOD((void), kick, (std::string), (const, override));
    MOCK_METHOD((bool), performCommand, (std::string), (const, override));
    MOCK_METHOD((bool), isSneaking, (), (const, override));
    MOCK_METHOD((void), setSneaking, (bool), (override));
    MOCK_METHOD((bool), isSprinting, (), (const, override));
    MOCK_METHOD((void), setSprinting, (bool), (override));
    MOCK_METHOD((void), playSound, (Location, std::string, float, float), (override));
    MOCK_METHOD((void), stopSound, (std::string), (override));
    MOCK_METHOD((void), stopAllSounds, (), (override));
    MOCK_METHOD((void), giveExp, (int), (override));
    MOCK_METHOD((void), giveExpLevels, (int), (override));
    MOCK_METHOD((float), getExpProgress, (), (const, override));
    MOCK_METHOD((void), setExpProgress, (float), (override));
    MOCK_METHOD((int), getExpLevel, (), (const, override));
    MOCK_METHOD((void), setExpLevel, (int), (override));
    MOCK_METHOD((int), getTotalExp, (), (const, override));
    MOCK_METHOD((bool), getAllowFlight, (), (const, override));
    MOCK_METHOD((void), setAllowFlight, (bool), (override));
    MOCK_METHOD((bool), isFlying, (), (const, override));
    MOCK_METHOD((void), setFlying, (bool), (override));
    MOCK_METHOD((float), getFlySpeed, (), (const, override));
    MOCK_METHOD((void), setFlySpeed, (float), (const, override));
    MOCK_METHOD((float), getWalkSpeed, (), (const, override));
    MOCK_METHOD((void), setWalkSpeed, (float), (const, override));
    MOCK_METHOD((Scoreboard &), getScoreboard, (), (const, override));
    MOCK_METHOD((void), setScoreboard, (Scoreboard &), (override));
    MOCK_METHOD((void), sendPopup, (std::string), (const, override));
    MOCK_METHOD((void), sendTip, (std::string), (const, override));
    MOCK_METHOD((void), sendToast, (std::string, std::string), (const, override));
    MOCK_METHOD((void), sendTitle, (std::string, std::string), (const, override));
    MOCK_METHOD((void), sendTitle, (std::string, std::string, int, int, int), (const, override));
    MOCK_METHOD((void), resetTitle, (), (const, override));
    MOCK_METHOD((void), spawnParticle, (std::string, Location), (const, override));
    MOCK_METHOD((void), spawnParticle, (std::string, float, float, float), (const, override));
    MOCK_METHOD((void), spawnParticle, (std::string, Location, std::optional<std::string>), (const, override));
    MOCK_METHOD((void), spawnParticle, (std::string, float, float, float, std::optional<std::string>),
                (const, override));
    MOCK_METHOD((std::chrono::milliseconds), getPing, (), (const, override));
    MOCK_METHOD((std::string), getLocale, (), (const, override));
    MOCK_METHOD((void), updateCommands, (), (const, override));
    MOCK_METHOD((PlayerInventory &), getInventory, (), (const, override));
    MOCK_METHOD((Inventory &), getEnderChest, (), (const, override));
    MOCK_METHOD((GameMode), getGameMode, (), (const, override));
    MOCK_METHOD((void), setGameMode, (GameMode), (override));
    MOCK_METHOD((std::string), getDeviceOS, (), (const, override));
    MOCK_METHOD((std::string), getDeviceId, (), (const, override));
    MOCK_METHOD((std::string), getGameVersion, (), (const, override));
    MOCK_METHOD((Skin), getSkin, (), (const, override));
    MOCK_METHOD((void), sendForm, (FormVariant), (override));
    MOCK_METHOD((void), closeForm, (), (override));
    MOCK_METHOD((void), sendPacket, (int, std::string_view), (const, override));
    MOCK_METHOD((void), sendMap, (MapView &), (override));
};
class GameplayMockDimension : public Dimension {
public:
    MOCK_METHOD((std::string), getName, (), (const, override));
    MOCK_METHOD((Type), getType, (), (const, override));
    MOCK_METHOD((Level &), getLevel, (), (const, override));
    MOCK_METHOD((std::unique_ptr<Block>), getBlockAt, (int, int, int), (const, override));
    MOCK_METHOD((std::unique_ptr<Block>), getBlockAt, (Location), (const, override));
    MOCK_METHOD((int), getHighestBlockYAt, (int, int), (const, override));
    MOCK_METHOD((std::unique_ptr<Block>), getHighestBlockAt, (int, int), (const, override));
    MOCK_METHOD((std::unique_ptr<Block>), getHighestBlockAt, (Location), (const, override));
    MOCK_METHOD((std::vector<std::unique_ptr<Chunk>>), getLoadedChunks, (), (override));
    MOCK_METHOD((Item &), dropItem, (Location, const ItemStack &), (override));
    MOCK_METHOD((Actor *), spawnActor, (Location, std::string), (override));
    MOCK_METHOD((std::vector<Actor *>), getActors, (), (const, override));
};
class GameplayMockLevel : public Level {
public:
    MOCK_METHOD((std::string), getName, (), (const, override));
    MOCK_METHOD((std::vector<Actor *>), getActors, (), (const, override));
    MOCK_METHOD((int), getTime, (), (const, override));
    MOCK_METHOD((void), setTime, (int), (override));
    MOCK_METHOD((std::vector<Dimension *>), getDimensions, (), (const, override));
    MOCK_METHOD((Dimension *), getDimension, (std::string), (const, override));
    MOCK_METHOD((std::int64_t), getSeed, (), (const, override));
};
class GameplayMockBlock : public Block {
public:
    MOCK_METHOD((std::string), getType, (), (const, override));
    MOCK_METHOD((void), setType, (std::string), (override));
    MOCK_METHOD((void), setType, (std::string, bool), (override));
    MOCK_METHOD((std::unique_ptr<BlockData>), getData, (), (const, override));
    MOCK_METHOD((void), setData, (const BlockData &), (override));
    MOCK_METHOD((void), setData, (const BlockData &, bool), (override));
    MOCK_METHOD((std::unique_ptr<Block>), getRelative, (int, int, int), (override));
    MOCK_METHOD((std::unique_ptr<Block>), getRelative, (BlockFace), (override));
    MOCK_METHOD((std::unique_ptr<Block>), getRelative, (BlockFace, int), (override));
    MOCK_METHOD((Dimension &), getDimension, (), (const, override));
    MOCK_METHOD((int), getX, (), (const, override));
    MOCK_METHOD((int), getY, (), (const, override));
    MOCK_METHOD((int), getZ, (), (const, override));
    MOCK_METHOD((Location), getLocation, (), (const, override));
    MOCK_METHOD((std::unique_ptr<BlockState>), captureState, (), (const, override));
    MOCK_METHOD((std::unique_ptr<Block>), clone, (), (const, override));
};
}  // namespace endstone
