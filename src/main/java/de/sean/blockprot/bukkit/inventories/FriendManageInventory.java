package de.sean.blockprot.bukkit.inventories;

import de.sean.blockprot.BlockProt;
import de.sean.blockprot.TranslationKey;
import de.sean.blockprot.Translator;
import de.sean.blockprot.bukkit.nbt.BlockAccessFlag;
import de.sean.blockprot.bukkit.nbt.BlockLockHandler;
import de.sean.blockprot.bukkit.nbt.LockUtil;
import de.sean.blockprot.util.ItemUtil;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTEntity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class FriendManageInventory extends FriendModifyInventory {
    private int maxSkulls = InventoryConstants.tripleLine - 5;

    private static final List<EnumSet<BlockAccessFlag>> accessFlagCombinations = Arrays.asList(
        EnumSet.of(BlockAccessFlag.READ),
        EnumSet.of(BlockAccessFlag.WRITE),
        EnumSet.of(BlockAccessFlag.READ, BlockAccessFlag.WRITE)
    );

    @Override
    public int getSize() {
        return InventoryConstants.tripleLine;
    }

    @NotNull
    @Override
    public String getTranslatedInventoryName() {
        return Translator.get(TranslationKey.INVENTORIES__FRIENDS__MANAGE);
    }

    @NotNull
    private String accessFlagToString(EnumSet<BlockAccessFlag> flags) {
        if (flags.isEmpty()) return "No access";
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (BlockAccessFlag flag : flags) {
            String flagStr = flag.toString();
            builder
                .append(flagStr.substring(0, 1).toUpperCase(Locale.ENGLISH)) // Uppercase first letter.
                .append(flagStr.substring(1).toLowerCase(Locale.ENGLISH));
            if (i < (flags.size() - 1)) builder.append(", ");
            i++;
        }
        return builder.toString();
    }

    private int getAccessFlagIndexOf(EnumSet<BlockAccessFlag> flags) {
        int result = 0;
        for (; result < accessFlagCombinations.size(); result++) {
            if (flags.equals(accessFlagCombinations.get(result))) return result;
        }
        return result;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event, @Nullable InventoryState state) {
        final Player player = (Player)event.getWhoClicked();
        final ItemStack item = event.getCurrentItem();
        if (item == null) return;
        switch (item.getType()) {
            case BLACK_STAINED_GLASS_PANE: {
                // Exit the modify inventory and return to the base lock inventory.
                if (state == null) break;
                exitModifyInventory(player, state);
                break;
            }
            case CYAN_STAINED_GLASS_PANE: {
                if (state != null && state.getFriendPage() >= 1) {
                    state.setFriendPage(state.getFriendPage() - 1);

                    player.closeInventory();
                    player.openInventory(fill(player));
                }
                break;
            }
            case BLUE_STAINED_GLASS_PANE: {
                ItemStack lastFriendInInventory = event.getInventory().getItem(maxSkulls);
                if (lastFriendInInventory != null && lastFriendInInventory.getAmount() == 0 && state != null) {
                    // There's an item in the last slot => The page is fully filled up, meaning we should go to the next page.
                    state.setFriendPage(state.getFriendPage() + 1);

                    player.closeInventory();
                    player.openInventory(fill(player));
                }
                break;
            }
            case SKELETON_SKULL:
            case PLAYER_HEAD: {
                // Get the clicked player head and open the detail inventory.
                if (state == null) break;
                int index = findItemIndex(item);
                OfflinePlayer friend = state.getFriendResultCache().get(index);
                state.setCurFriend(friend);
                final Inventory inv = new FriendDetailInventory().fill(player);
                player.closeInventory();
                player.openInventory(inv);
                break;
            }
            case MAP: {
                FriendSearchInventory.INSTANCE.openAnvilInventory(player);
                break;
            }
            case OAK_DOOR: {
                if (state == null) break;
                BlockLockHandler handler = new BlockLockHandler(Objects.requireNonNull(state.getBlock()));
                int curIndex = getAccessFlagIndexOf(handler.getBlockAccessFlags());
                if (curIndex + 1 >= accessFlagCombinations.size()) curIndex = 0;
                else curIndex += 1;
                EnumSet<BlockAccessFlag> newFlags = accessFlagCombinations.get(curIndex);
                setItemStack(
                    InventoryConstants.tripleLine - 3,
                    Material.OAK_DOOR,
                    accessFlagToString(newFlags)
                );
                handler.setBlockAccessFlags(newFlags);
                break;
            }
            default: {
                // Unexpected, exit the inventory.
                player.closeInventory();
                InventoryState.Companion.remove(player.getUniqueId());
                break;
            }
        }
        event.setCancelled(true);
    }

    @NotNull
    public Inventory fill(@NotNull Player player) {
        final InventoryState state = InventoryState.Companion.get(player.getUniqueId());
        if (state == null) return inventory;

        List<OfflinePlayer> players;
        EnumSet<BlockAccessFlag> flags;
        switch (state.getFriendSearchState()) {
            case FRIEND_SEARCH: {
                final BlockLockHandler handler = new BlockLockHandler(Objects.requireNonNull(state.getBlock()));
                players = mapUuidToPlayer(handler.getAccess());
                flags = handler.getBlockAccessFlags();
                break;
            }
            case DEFAULT_FRIEND_SEARCH: {
                maxSkulls += 1; // We have 1 button less, as that button is only for blocks, which gives us room for one more friend.
                final NBTCompound nbtEntity = new NBTEntity(player).getPersistentDataContainer();
                List<String> currentFriends = LockUtil.parseStringList(nbtEntity.getString(LockUtil.DEFAULT_FRIENDS_ATTRIBUTE));
                final String selfUuid = player.getUniqueId().toString();
                players = filterList(
                    currentFriends,
                    Arrays.asList(Bukkit.getOfflinePlayers()),
                    (uuid, cur) -> cur.contains(uuid) && !uuid.equals(selfUuid)
                );
                flags = null;
                break;
            }
            default: {
                throw new RuntimeException("Could not build " + this.getClass().getName() + " due to invalid friend search state: " + state.getFriendSearchState());
            }
        }

        // Fill the first page inventory with skeleton skulls.
        state.getFriendResultCache().clear();
        int pageOffset = maxSkulls * state.getFriendPage();
        for (int i = pageOffset; i < Math.min(players.size() - pageOffset, maxSkulls); i++) {
            final OfflinePlayer curPlayer = players.get(i);
            inventory.setItem(i - pageOffset, ItemUtil.INSTANCE.getItemStack(1, Material.SKELETON_SKULL, curPlayer.getName()));
            state.getFriendResultCache().add(curPlayer);
        }

        // Only show the page buttons if there's more than 1 page.
        if (state.getFriendPage() == 0 && players.size() >= maxSkulls) {
            setItemStack(
                maxSkulls,
                Material.CYAN_STAINED_GLASS_PANE,
                TranslationKey.INVENTORIES__LAST_PAGE
            );
            setItemStack(
                maxSkulls + 1,
                Material.BLUE_STAINED_GLASS_PANE,
                TranslationKey.INVENTORIES__NEXT_PAGE
            );
        }

        // Only show the access switch
        if (flags != null && state.getFriendSearchState() != InventoryState.FriendSearchState.DEFAULT_FRIEND_SEARCH) {
            setItemStack(
                InventoryConstants.tripleLine - 3,
                Material.OAK_DOOR,
                accessFlagToString(flags)
            );
        }
        setItemStack(
            InventoryConstants.tripleLine - 2,
            Material.MAP,
            TranslationKey.INVENTORIES__FRIENDS__SEARCH
        );
        setBackButton();

        Bukkit.getScheduler().runTaskAsynchronously(BlockProt.instance, () -> {
            int i = 0;
            while (i < maxSkulls && i < state.getFriendResultCache().size()) {
                inventory.setItem(
                    i,
                    ItemUtil.INSTANCE.getPlayerSkull(state.getFriendResultCache().get(i))
                );
                i++;
            }
        });

        return inventory;
    }
}
