package net.sf.freecol.server.model;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.control.ChangeSet;
import net.sf.freecol.server.control.ChangeSet.See;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerPlayerCombat;

public class BurnMissions extends ServerPlayerCombat{

	
	public BurnMissions(Game game) {
		super(game);
	
	}
	
	  public void csBurnMissions(Unit attacker, IndianSettlement settlement,
	    		ChangeSet cs) {
		  
		  ServerPlayer attackerPlayer = (ServerPlayer) attacker.getOwner();
	    	StringTemplate attackerNation = attackerPlayer.getNationName();
	    	ServerPlayer nativePlayer = (ServerPlayer) settlement.getOwner();
	    	StringTemplate nativeNation = nativePlayer.getNationName();

	    	// Message only for the European player
	    	cs.addMessage(See.only(attackerPlayer),
	    			new ModelMessage(ModelMessage.MessageType.COMBAT_RESULT,
	    					"model.unit.burnMissions", attacker, settlement)
	    	.addStringTemplate("%nation%", attackerNation)
	    	.addStringTemplate("%enemyNation%", nativeNation));

	    	// Burn down the missions
	    	for (IndianSettlement s : nativePlayer.getIndianSettlements()) {
	    		if (s.getMissionary(attackerPlayer) != null) {
	    			csKillMissionary(s, null, cs);
	    		}
	    	}
		  
	  }
}
