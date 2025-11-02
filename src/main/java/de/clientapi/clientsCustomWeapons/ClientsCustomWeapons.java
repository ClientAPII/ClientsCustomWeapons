package de.clientapi.clientsCustomWeapons;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import de.clientapi.clientsCustomWeapons.command.CcwCommand;
import de.clientapi.clientsCustomWeapons.config.WeaponRegistry;
import de.clientapi.clientsCustomWeapons.combat.ShieldBlocker;
import de.clientapi.clientsCustomWeapons.combat.WeaponRangeHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClientsCustomWeapons extends JavaPlugin {

    private WeaponRegistry registry;

    @Override
    public void onEnable() {
        saveResource("weapons.yml", false);
        this.registry = new WeaponRegistry(this);


        Bukkit.getPluginManager().registerEvents(new ShieldBlocker(this), this);

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("Protocollib not found, disabling weapon range support.");
        } else {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            new WeaponRangeHandler(this, pm).register();
        }

        getCommand("ccw").setExecutor(new CcwCommand(this, registry));

        getLogger().info("ClientsCustomWeapons enabled.");
    }

    public WeaponRegistry registry() { return registry; }
}
