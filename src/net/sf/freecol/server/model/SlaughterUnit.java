package net.sf.freecol.server.model;

import net.sf.freecol.common.model.EquipmentType;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.control.ChangeSet;
import net.sf.freecol.server.control.ChangeSet.See;

public class SlaughterUnit extends ServerPlayerCombat{

	public SlaughterUnit(Game game) {
		super(game);
	
	}
	
	
	/**
     * Slaughter a unit.
     *
     * @param winner The <code>Unit</code> that is slaughtering.
     * @param loser The <code>Unit</code> to slaughter.
     * @param cs A <code>ChangeSet</code> to update.
     */
    public void csSlaughterUnit(Unit winner, Unit loser, ChangeSet cs) {
        ServerPlayer winnerPlayer = (ServerPlayer) winner.getOwner();
        StringTemplate winnerNation = winner.getApparentOwnerName();
        StringTemplate winnerLocation = winner.getLocation()
            .getLocationNameFor(winnerPlayer);
        ServerPlayer loserPlayer = (ServerPlayer) loser.getOwner();
        StringTemplate loserNation = loser.getApparentOwnerName();
        StringTemplate loserLocation = loser.getLocation()
            .getLocationNameFor(loserPlayer);
        String messageId = loser.getType().getId() + ".destroyed";

        cs.addMessage(See.only(winnerPlayer),
            new ModelMessage(ModelMessage.MessageType.COMBAT_RESULT,
                messageId, winner)
            .setDefaultId("model.unit.unitSlaughtered")
            .addStringTemplate("%nation%", loserNation)
            .addStringTemplate("%unit%", loser.getLabel())
            .addStringTemplate("%enemyNation%", winnerPlayer.getNationName())
            .addStringTemplate("%enemyUnit%", winner.getLabel())
            .addStringTemplate("%location%", winnerLocation));
        cs.addMessage(See.only(loserPlayer),
            new ModelMessage(ModelMessage.MessageType.COMBAT_RESULT,
                messageId, loser.getTile())
            .setDefaultId("model.unit.unitSlaughtered")
            .addStringTemplate("%nation%", loserPlayer.getNationName())
            .addStringTemplate("%unit%", loser.getLabel())
            .addStringTemplate("%enemyNation%", winnerNation)
            .addStringTemplate("%enemyUnit%", winner.getLabel())
            .addStringTemplate("%location%", loserLocation));
        if (loserPlayer.isIndian() && loserPlayer.checkForDeath() == IS_DEAD) {
            StringTemplate nativeNation = loserPlayer.getNationName();
            cs.addGlobalHistory(getGame(),
                new HistoryEvent(getGame().getTurn(),
                    HistoryEvent.EventType.DESTROY_NATION)
                .addStringTemplate("%nation%", winnerNation)
                .addStringTemplate("%nativeNation%", nativeNation));
        }

        // Transfer equipment, do not generate messages for the loser.
        EquipmentType equip;
        while ((equip = loser.getBestCombatEquipmentType(loser.getEquipment()))
               != null) {
            loser.changeEquipment(equip, -loser.getEquipmentCount(equip));
            csCaptureEquipment(winner, loser, equip, cs);
        }

        // Destroy unit.
        cs.addDispose(See.perhaps().always(loserPlayer),
            loser.getLocation(), loser);
    }
    	
//	}

}
