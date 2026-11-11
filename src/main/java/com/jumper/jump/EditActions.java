package com.jumper.jump;

import com.jumper.data.JumperData;
import com.jumper.data.PlayerRecord;
import com.jumper.net.EditPayload;
import net.minecraft.server.level.ServerPlayer;

/** What the server does with an edit from the name box or the list screen. Shared by both loaders' network code. */
public final class EditActions {
    /** Most edits one player may send in a second; a person clicking fast stays well under it. */
    private static final int MAX_EDITS_PER_SECOND = 20;

    private EditActions() {
    }

    public static void handle(EditPayload payload, ServerPlayer player) {
        PlayerRecord record = JumperData.get(player.server).player(player.getUUID());
        if (record == null) {
            JumperSync.send(player);
            return;
        }
        if (!record.allowEdit(player.server.overworld().getGameTime(), MAX_EDITS_PER_SECOND)) {
            return;
        }
        // Set first, so whichever sync this edit causes tells the client its edit has been handled.
        record.setLastEditSeq(payload.seq());
        JumpService.Result result;
        switch (payload.action()) {
            case EditPayload.SAVE -> result = JumpService.save(player, payload.text());
            case EditPayload.RENAME -> result = JumpService.rename(player, payload.index(), payload.expected(),
                    payload.text());
            case EditPayload.PICTURE -> result = JumpService.setPicture(player, payload.index(), payload.expected());
            case EditPayload.MOVE_UP -> result = JumpService.move(player, payload.index(), payload.expected(), -1);
            case EditPayload.MOVE_DOWN -> result = JumpService.move(player, payload.index(), payload.expected(), 1);
            case EditPayload.DELETE -> result = JumpService.removeAt(player, payload.index(), payload.expected());
            case EditPayload.RESTORE -> result = JumpService.restore(player, payload.index(), payload.expected());
            case EditPayload.PICTURE_SET -> result = JumpService.setPictureId(player, payload.index(),
                    payload.expected(), payload.text());
            default -> {
                JumperSync.send(player);
                return;
            }
        }
        if (!result.message().getString().isEmpty()) {
            player.displayClientMessage(result.message(), true);
        }
        if (!result.ok()) {
            // Nothing changed, so nothing was sent yet; the client still needs its edit answered.
            JumperSync.send(player);
        }
    }
}
