package local.mods.middleclickpearl;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public final class MiddleClickPearlClient implements ClientModInitializer {
    private static KeyBinding throwPearlKey;
    private static int restoreDelay = 0;
    private static int originalSelectedSlot = -1;
    private static int swappedInventorySlot = -1;
    private static int swappedHotbarSlot = -1;

    @Override
    public void onInitializeClient() {
        throwPearlKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.middleclickpearl.throw",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                KeyBinding.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(MiddleClickPearlClient::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        // Restore the original inventory arrangement a few ticks after the throw.
        if (restoreDelay > 0) {
            restoreDelay--;
            if (restoreDelay == 0) {
                restoreInventory(client);
            }
        }

        if (client.currentScreen != null || restoreDelay > 0) {
            return;
        }

        while (throwPearlKey.wasPressed()) {
            throwPearl(client);
        }
    }

    private static void throwPearl(MinecraftClient client) {
        var player = client.player;
        int selected = player.getInventory().getSelectedSlot();
        int pearlSlot = -1;

        // Inventory indices: 0-8 hotbar, 9-35 main inventory.
        for (int slot = 0; slot < 36; slot++) {
            if (player.getInventory().getStack(slot).isOf(Items.ENDER_PEARL)) {
                pearlSlot = slot;
                break;
            }
        }

        if (pearlSlot < 0) {
            player.sendMessage(Text.literal("Middle Click Pearl: эндер-жемчуг не найден"), true);
            return;
        }

        originalSelectedSlot = selected;
        swappedInventorySlot = -1;
        swappedHotbarSlot = -1;

        if (pearlSlot < 9) {
            player.getInventory().setSelectedSlot(pearlSlot);
        } else {
            // Swap the pearl into the currently selected hotbar slot.
            client.interactionManager.clickSlot(
                    player.playerScreenHandler.syncId,
                    pearlSlot,
                    selected,
                    SlotActionType.SWAP,
                    player
            );
            swappedInventorySlot = pearlSlot;
            swappedHotbarSlot = selected;
        }

        client.interactionManager.interactItem(player, Hand.MAIN_HAND);

        // Packet order is preserved; restore after the use packet has been sent.
        restoreDelay = 4;
    }

    private static void restoreInventory(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            clearRestoreState();
            return;
        }

        if (swappedInventorySlot >= 0) {
            client.interactionManager.clickSlot(
                    client.player.playerScreenHandler.syncId,
                    swappedInventorySlot,
                    swappedHotbarSlot,
                    SlotActionType.SWAP,
                    client.player
            );
        }

        if (originalSelectedSlot >= 0) {
            client.player.getInventory().setSelectedSlot(originalSelectedSlot);
        }
        clearRestoreState();
    }

    private static void clearRestoreState() {
        originalSelectedSlot = -1;
        swappedInventorySlot = -1;
        swappedHotbarSlot = -1;
        restoreDelay = 0;
    }
}
