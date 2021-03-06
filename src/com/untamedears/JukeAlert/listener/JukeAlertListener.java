package com.untamedears.JukeAlert.listener;

import static com.untamedears.JukeAlert.util.Utility.doesSnitchExist;
import static com.untamedears.JukeAlert.util.Utility.isDebugging;
import static com.untamedears.JukeAlert.util.Utility.isOnSnitch;
import static com.untamedears.JukeAlert.util.Utility.isPartialOwnerOfSnitch;
import static com.untamedears.JukeAlert.util.Utility.notifyGroup;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.material.Lever;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;

import vg.civcraft.mc.citadel.Citadel;
import vg.civcraft.mc.citadel.ReinforcementManager;
import vg.civcraft.mc.citadel.events.ReinforcementCreationEvent;
import vg.civcraft.mc.citadel.reinforcement.PlayerReinforcement;
import vg.civcraft.mc.citadel.reinforcement.Reinforcement;
import vg.civcraft.mc.namelayer.events.GroupDeleteEvent;
import vg.civcraft.mc.namelayer.events.GroupMergeEvent;
import vg.civcraft.mc.namelayer.group.Group;

import com.untamedears.JukeAlert.JukeAlert;
import com.untamedears.JukeAlert.external.Mercury;
import com.untamedears.JukeAlert.external.VanishNoPacket;
import com.untamedears.JukeAlert.manager.PlayerManager;
import com.untamedears.JukeAlert.manager.SnitchManager;
import com.untamedears.JukeAlert.model.Snitch;

public class JukeAlertListener implements Listener {

	private ReinforcementManager rm = Citadel.getReinforcementManager();
    private final JukeAlert plugin = JukeAlert.getInstance();
    SnitchManager snitchManager = plugin.getSnitchManager();
    PlayerManager playerManager = plugin.getPlayerManager();
    private final Map<UUID, Set<Snitch>> playersInSnitches = new TreeMap<UUID, Set<Snitch>>();
    private final ArrayList<Location> previousLocations = new ArrayList<Location>();
    private final VanishNoPacket vanishNoPacket = new VanishNoPacket();
    private final Mercury mercury = new Mercury();

    private boolean checkProximity(Snitch snitch, UUID accountId) {
        Set<Snitch> inList = playersInSnitches.get(accountId);
        if (inList == null) {
            inList = new TreeSet<Snitch>();
            playersInSnitches.put(accountId, inList);
        }
        return inList.contains(snitch);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (vanishNoPacket.isPlayerInvisible(player)) {
            return;
        }
        UUID accountId = player.getUniqueId();
        Set<Snitch> inList = new TreeSet<Snitch>();
        playersInSnitches.put(accountId, inList);

        Location location = player.getLocation();
        World world = location.getWorld();
        Set<Snitch> snitches = snitchManager.findSnitches(world, location);
        for (Snitch snitch : snitches) {
            if (!isOnSnitch(snitch, accountId)) {
                snitch.imposeSnitchTax();
                inList.add(snitch);
                try {
                String message = ChatColor.AQUA + " * " + player.getDisplayName() + " logged in to snitch at " 
				        + snitch.getName() + " [" + snitch.getLoc().getWorld().getName() + " " + snitch.getX() + 
				        " " + snitch.getY() + " " + snitch.getZ() + "]";
                notifyGroup(snitch, message);
                                
                if (mercury.isEnabled())
                	mercury.sendMessage(snitch.getGroup().getName() + " " + message, "login");
                } catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                if (snitch.shouldLog()) {
                    plugin.getJaLogger().logSnitchLogin(snitch, location, player);

                	Location north = new Location(world, snitch.getX(), snitch.getY(), snitch.getZ()-1);
                	toggleLeverIfApplicable(snitch, north, true);
                }
            }
        }
    }

    public void handlePlayerExit(PlayerEvent event) {
        Player player = event.getPlayer();

        if (vanishNoPacket.isPlayerInvisible(player)) {
            return;
        }
        UUID accountId = player.getUniqueId();
        playersInSnitches.remove(accountId);

        Location location = player.getLocation();
        World world = location.getWorld();
        Set<Snitch> snitches = snitchManager.findSnitches(world, location);
        for (Snitch snitch : snitches) {
            if (!isOnSnitch(snitch, accountId)) {
                snitch.imposeSnitchTax();
                try {
                String message = ChatColor.AQUA + " * " + player.getDisplayName() + " logged out in snitch at " 
		        		+ snitch.getName() + " [" + snitch.getLoc().getWorld().getName() + " " + snitch.getX() + 
		                " " + snitch.getY() + " " + snitch.getZ() + "]";
                notifyGroup(snitch, message);
                
                if (mercury.isEnabled())
                	mercury.sendMessage(snitch.getGroup().getName() + " " + message, "logout");
                } catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                if (snitch.shouldLog()) {
                    plugin.getJaLogger().logSnitchLogout(snitch, location, player);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void playerKickEvent(PlayerKickEvent event) {
        handlePlayerExit(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerQuitEvent(PlayerQuitEvent event) {
        handlePlayerExit(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void placeSnitchBlock(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Location loc = block.getLocation();
        if (block.getType().equals(Material.JUKEBOX)) {
            if (!rm.isReinforced(loc)) {
                player.sendMessage(ChatColor.YELLOW + "You've placed a jukebox; reinforce it to register it as a snitch.");
            }
        } else if (block.getType().equals(Material.NOTE_BLOCK)) {
            if (!rm.isReinforced(loc)) {
                player.sendMessage(ChatColor.YELLOW + "You've placed a noteblock; reinforce it to register it as an entry snitch.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void reinforceSnitchBlock(ReinforcementCreationEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType().equals(Material.JUKEBOX)) {

            Player player = event.getPlayer();
            Location loc = block.getLocation();
            Reinforcement rei = event.getReinforcement();
            if (rei instanceof PlayerReinforcement) {
                PlayerReinforcement reinforcement = (PlayerReinforcement) rei;
                Group owner = reinforcement.getGroup();
                if (owner == null) {
                    JukeAlert.getInstance().log(String.format(
                    		"No group on rein (%s)", reinforcement.getLocation().toString()));
                }
                    Snitch snitch;
                    if (snitchManager.getSnitch(loc.getWorld(), loc) != null) {
                        snitch = snitchManager.getSnitch(loc.getWorld(), loc);
                        plugin.getJaLogger().updateSnitchGroup(snitchManager.getSnitch(loc.getWorld(), loc), owner.getName());
                        snitchManager.removeSnitch(snitch);
                        snitch.setGroup(owner);
                    } else {
                        snitch = new Snitch(loc, owner, true, false);
                        plugin.getJaLogger().logSnitchPlace(player.getWorld().getName(), owner.getName(), "", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), true);
                        snitch.setId(plugin.getJaLogger().getLastSnitchID());
                        plugin.getJaLogger().increaseLastSnitchID();
                    }
                    snitchManager.addSnitch(snitch);

                    player.sendMessage(ChatColor.AQUA + "You've created a snitch block registered to the group " + owner.getName() + ".  To name your snitch, type /janame.");
            }
        } else if (block.getType().equals(Material.NOTE_BLOCK)) {
            Player player = event.getPlayer();
            Location loc = block.getLocation();
            Reinforcement rei = event.getReinforcement();
            if (rei instanceof PlayerReinforcement) {
                PlayerReinforcement reinforcement = (PlayerReinforcement) rei;
                Group owner = reinforcement.getGroup();
                if (owner == null) {
                    JukeAlert.getInstance().log(String.format(
                            "No group on rein (%s)", reinforcement.getLocation().toString()));
                }
                    Snitch snitch;
                    if (snitchManager.getSnitch(loc.getWorld(), loc) != null) {
                        snitch = snitchManager.getSnitch(loc.getWorld(), loc);
                        plugin.getJaLogger().updateSnitchGroup(snitchManager.getSnitch(loc.getWorld(), loc), owner.getName());
                        snitchManager.removeSnitch(snitch);
                        snitch.setGroup(owner);
                    } else {
                        snitch = new Snitch(loc, owner, false, false);
                        plugin.getJaLogger().logSnitchPlace(player.getWorld().getName(), owner.getName(), "", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), false);
                        snitch.setId(plugin.getJaLogger().getLastSnitchID());
                        plugin.getJaLogger().increaseLastSnitchID();
                    }
                    snitchManager.addSnitch(snitch);

                    player.sendMessage(ChatColor.AQUA + "You've created an entry snitch registered to the group " + owner.getName() + ".  To name your entry snitch, type /janame.");
                }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGroupDeletion(GroupDeleteEvent event) {
        String groupName = event.getGroup().getName();
        Set<Snitch> removeSet = new TreeSet<Snitch>();
        for (Snitch snitch : snitchManager.getAllSnitches()) {
            final Group snitchGroup = snitch.getGroup();
            String snitchGroupName = null;
            if (snitchGroup != null) {
                snitchGroupName = snitchGroup.getName();
            }
            if (snitchGroupName != null && snitchGroupName.equalsIgnoreCase(groupName)) {
                removeSet.add(snitch);
            }
        }
        for (Snitch snitch : removeSet) {
            final Location loc = snitch.getLoc();
            if (snitch.shouldLog()) {
                plugin.getJaLogger().logSnitchBreak(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            }
            snitchManager.removeSnitch(snitch);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onGroupMergeEvent(GroupMergeEvent event){
    	Group g1 = event.getMergingInto();
    	Group g2 = event.getToBeMerged();
    	String groupName = g2.getName();
        Set<Snitch> mergeSet = new TreeSet<Snitch>();
        for (Snitch snitch : snitchManager.getAllSnitches()) {
            final Group snitchGroup = snitch.getGroup();
            String snitchGroupName = null;
            if (snitchGroup != null) {
                snitchGroupName = snitchGroup.getName();
            }
            if (snitchGroupName != null && snitchGroupName.equalsIgnoreCase(groupName)) {
            	mergeSet.add(snitch);
            }
        }
        for (Snitch snitch : mergeSet) {
        	snitch.setGroup(g1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void breakSnitchBlock(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Block block = event.getBlock();
        if (!block.getType().equals(Material.JUKEBOX)) {
            return;
        }
        if (vanishNoPacket.isPlayerInvisible(event.getPlayer())) {
            return;
        }
        Location loc = block.getLocation();
        if (snitchManager.getSnitch(loc.getWorld(), loc) != null) {
            Snitch snitch = snitchManager.getSnitch(loc.getWorld(), loc);
            if (snitch.shouldLog()) {
                plugin.getJaLogger().logSnitchBreak(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            }
            snitchManager.removeSnitch(snitch);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void enterSnitchProximity(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld().equals(to.getWorld())) {
            // Player didn't move by at least one block.
            return;
        }
        Player player = event.getPlayer();
        if (vanishNoPacket.isPlayerInvisible(player)) {
            return;
        }
        UUID accountId = player.getUniqueId();
        Location location = player.getLocation();
        World world = location.getWorld();
        Set<Snitch> inList = playersInSnitches.get(accountId);
        if (inList == null) {
            inList = new TreeSet<Snitch>();
            playersInSnitches.put(accountId, inList);
        }
        Set<Snitch> snitches = snitchManager.findSnitches(world, location);
        for (Snitch snitch : snitches) {
            if (doesSnitchExist(snitch, true)) {

                if (isPartialOwnerOfSnitch(snitch, accountId)) {
                    if (!inList.contains(snitch)) {
                        inList.add(snitch);
                        plugin.getJaLogger().logSnitchVisit(snitch);
                    }
                }

                if ((!isOnSnitch(snitch, accountId) || isDebugging())) {
                    if (!inList.contains(snitch)) {
                        snitch.imposeSnitchTax();
                        inList.add(snitch);
                        if ((plugin.getConfigManager().getInvisibilityEnabled() && player.hasPotionEffect(PotionEffectType.INVISIBILITY))
                        		&& !snitch.shouldLog()) 
                        	continue;
                        else{
                        	try {
                        	String message = ChatColor.AQUA + " * " + player.getDisplayName() + " entered snitch at " 
					        		+ snitch.getName() + " [" + snitch.getLoc().getWorld().getName() + " " + snitch.getX() + 
					                " " + snitch.getY() + " " + snitch.getZ() + "]";
                            notifyGroup(snitch, message);
                            
                            if (mercury.isEnabled())
                            	mercury.sendMessage(snitch.getGroup().getName() + " " + message, "entry");
                        	} catch (SQLException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
                        }
                        if (snitch.shouldLog()){
                        	plugin.getJaLogger().logSnitchEntry(snitch, location, player);
                        	Location north = new Location(world, snitch.getX(), snitch.getY(), snitch.getZ()-1);
                        	toggleLeverIfApplicable(snitch, north, true);
                        }
                    }
                }

            }
        }
        snitches = snitchManager.findSnitches(world, location, true);
        Set<Snitch> rmList = new TreeSet<Snitch>();
        for (Snitch snitch : inList) {
            if (snitches.contains(snitch)) {
                continue;
            }
            rmList.add(snitch);
        }
        inList.removeAll(rmList);
    }
    
    // Exceptions:  No exceptions must be raised from this for any reason.
    private void toggleLeverIfApplicable(final Snitch snitch, final Location blockToPossiblyToggle, final Boolean leverShouldEnable)
    {
    	try
    	{
    		if(!JukeAlert.getInstance().getConfigManager().getAllowTriggeringLevers()) return;
	    	if (null == snitch) return;
	    	
	    	World world = snitch.getLoc().getWorld();
	    	if(snitch.shouldToggleLevers())
	        {
	        	if (world.getBlockAt(blockToPossiblyToggle).getType() == Material.LEVER)
	        	{
	        		BlockState leverState = world.getBlockAt(blockToPossiblyToggle).getState();
	        		Lever lever = ((Lever)leverState.getData());
	        		
	        		if(leverShouldEnable && !lever.isPowered())
	        		{
	        			lever.setPowered(true);
		        		leverState.setData(lever);
		        		leverState.update();
	        		}
	        		else if (!leverShouldEnable && lever.isPowered())
	        		{
	        			lever.setPowered(false);
		        		leverState.setData(lever);
		        		leverState.update();
	        		}
	        		
	        		if (leverShouldEnable)
	        		{
		        		BukkitScheduler scheduler = plugin.getServer().getScheduler();
			        	scheduler.scheduleSyncDelayedTask(plugin, new Runnable() {
		                    public void run() {
		                    	toggleLeverIfApplicable(snitch, blockToPossiblyToggle, false);
		                    }
		                }, 15L);
	        		}
	        	}
	        }
    	}
    	catch(Exception ex)
    	{
    		// eat.
    		return;
    	}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpenEvent(InventoryOpenEvent e) {
        Player player = (Player) e.getPlayer();
        if (e.isCancelled()) {
            return;
        }
        if (vanishNoPacket.isPlayerInvisible(player)) {
            return;
        }
        Block block;
        if (e.getInventory().getHolder() instanceof Chest) {
            Chest chest = (Chest) e.getInventory().getHolder();
            block = chest.getBlock();
        } else if (e.getInventory().getHolder() instanceof DoubleChest) {
            DoubleChest chest = (DoubleChest) e.getInventory().getHolder();
            block = chest.getLocation().getBlock();
        } else {
            return;
        }
        UUID accountId = player.getUniqueId();
        Set<Snitch> snitches = snitchManager.findSnitches(player.getWorld(), player.getLocation());
        for (Snitch snitch : snitches) {
            if (!snitch.shouldLog()) {
                continue;
            }
            if (!isOnSnitch(snitch, accountId) || isDebugging()) {
                if (checkProximity(snitch, accountId)) {
                    plugin.getJaLogger().logUsed(snitch, player, block);
                    
                	Location south = new Location(block.getWorld(), snitch.getX(), snitch.getY(), snitch.getZ()+1);
                	toggleLeverIfApplicable(snitch, south, true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerKillEntity(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        LivingEntity killer = entity.getKiller();
        // TODO: This should never be true, bug?
        if (entity instanceof Player) {
            return;
        }
        if (!(killer instanceof Player)) {
            return;
        }
        if (vanishNoPacket.isPlayerInvisible((Player) killer)) {
            return;
        }
        Player player = (Player) killer;
        UUID accountId = player.getUniqueId();
        Set<Snitch> snitches = snitchManager.findSnitches(player.getWorld(), player.getLocation());
        for (Snitch snitch : snitches) {
            if (!snitch.shouldLog()) {
                continue;
            }
            if (!isOnSnitch(snitch, accountId) || isDebugging()) {
                if (checkProximity(snitch, accountId)) {
                    plugin.getJaLogger().logSnitchEntityKill(snitch, player, entity);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerKillPlayer(PlayerDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player)) {
            return;
        }
        Player killed = event.getEntity();
        Player killer = killed.getKiller();
        if (vanishNoPacket.isPlayerInvisible(killer)) {
            return;
        }
        UUID accountId = killer.getUniqueId();
        Set<Snitch> snitches = snitchManager.findSnitches(killed.getWorld(), killed.getLocation());
        for (Snitch snitch : snitches) {
            if (!snitch.shouldLog()) {
                continue;
            }
            if (!isOnSnitch(snitch, accountId) || isDebugging()) {
                if (checkProximity(snitch, accountId) || checkProximity(snitch, accountId)) {
                    plugin.getJaLogger().logSnitchPlayerKill(snitch, killer, killed);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockIgniteEvent(BlockIgniteEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (vanishNoPacket.isPlayerInvisible(player)) {
            return;
        }
        Block block = event.getBlock();
        UUID accountId = player.getUniqueId();
        Set<Snitch> snitches = snitchManager.findSnitches(block.getWorld(), block.getLocation());
        for (Snitch snitch : snitches) {
            if (!snitch.shouldLog()) {
                continue;
            }
            if (!isOnSnitch(snitch, accountId) || isDebugging()) {
                if (checkProximity(snitch, accountId)) {
                    plugin.getJaLogger().logSnitchIgnite(snitch, player, block);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBurnEvent(BlockBurnEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Block block = event.getBlock();
        Set<Snitch> snitches = snitchManager.findSnitches(block.getWorld(), block.getLocation());
        for (Snitch snitch : snitches) {
            if (!snitch.shouldLog()) {
                continue;
            }
            if (snitch.getGroup() != null) {
                continue;
            }
            plugin.getJaLogger().logSnitchBlockBurn(snitch, block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerBreakBlock(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (vanishNoPacket.isPlayerInvisible(player)) {
            return;
        }
        Block block = event.getBlock();
        UUID accountId = player.getUniqueId();
        Set<Snitch> snitches = snitchManager.findSnitches(block.getWorld(), block.getLocation());
        for (Snitch snitch : snitches) {
            if (!snitch.shouldLog()) {
                continue;
            }
            if (!isOnSnitch(snitch, accountId) || isDebugging()) {
                if (checkProximity(snitch, accountId)) {
                    plugin.getJaLogger().logSnitchBlockBreak(snitch, player, block);
                	Location west = new Location(block.getWorld(), snitch.getX()-1, snitch.getY(), snitch.getZ());
                	toggleLeverIfApplicable(snitch, west, true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerPlaceBlock(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (vanishNoPacket.isPlayerInvisible(player)) {
            return;
        }
        Block block = event.getBlock();
        UUID accountId = player.getUniqueId();
        Set<Snitch> snitches = snitchManager.findSnitches(block.getWorld(), block.getLocation());
        for (Snitch snitch : snitches) {

            if (!snitch.shouldLog()) {
                continue;
            }
            if (!isOnSnitch(snitch, accountId) || isDebugging()) {
                if (checkProximity(snitch, accountId)) {
                    plugin.getJaLogger().logSnitchBlockPlace(snitch, player, block);

                    Location east = new Location(block.getWorld(), snitch.getX()+1, snitch.getY(), snitch.getZ());
                	toggleLeverIfApplicable(snitch, east, true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerFillBucket(PlayerBucketFillEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (vanishNoPacket.isPlayerInvisible(player)) {
            return;
        }
        Block block = event.getBlockClicked();
        UUID accountId = player.getUniqueId();
        Set<Snitch> snitches = snitchManager.findSnitches(block.getWorld(), block.getLocation());
        for (Snitch snitch : snitches) {
            if (!snitch.shouldLog()) {
                continue;
            }
            if (!isOnSnitch(snitch, accountId) || isDebugging()) {
                if (checkProximity(snitch, accountId)) {
                    plugin.getJaLogger().logSnitchBucketFill(snitch, player, block);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerEmptyBucket(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (vanishNoPacket.isPlayerInvisible(player)) {
            return;
        }
        Block block = event.getBlockClicked();
        UUID accountId = player.getUniqueId();
        Set<Snitch> snitches = snitchManager.findSnitches(block.getWorld(), block.getLocation());
        for (Snitch snitch : snitches) {
            if (!snitch.shouldLog()) {
                continue;
            }
            if (!isOnSnitch(snitch, accountId) || isDebugging()) {
                if (checkProximity(snitch, accountId)) {
                    plugin.getJaLogger().logSnitchBucketEmpty(snitch, player, block.getLocation(), player.getItemInHand());
                }
            }
        }
    }
}