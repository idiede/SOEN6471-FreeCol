package net.sf.freecol.server.model;

import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.CombatModel;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.CombatModel.CombatResult;
import net.sf.freecol.common.model.Player.Stance;
import net.sf.freecol.server.control.ChangeSet;
import net.sf.freecol.server.control.ChangeSet.See;

public class ServerPlayerCombat extends ServerPlayer implements ServerModelObject{
	
	
	  /**
     * Combat.
     *
     * @param attacker The <code>FreeColGameObject</code> that is attacking.
     * @param defender The <code>FreeColGameObject</code> that is defending.
     * @param crs A list of <code>CombatResult</code>s defining the result.
     * @param random A pseudo-random number source.
     * @param cs A <code>ChangeSet</code> to update.
     */
	
	private static final Logger logger = Logger.getLogger(ServerPlayer.class.getName());
	
	FreeColGameObject attacker;
    FreeColGameObject defender;
    List<CombatResult> crs;
    Random random;
    ChangeSet cs;
    
    public ServerPlayerCombat(Game game, String id){
    	super(game, id);
    	
       //implicitly call spComabt in the constructor ?? Not right now
       // csCombat( attacker, defender, crs, random, cs);
    	
    }
    
    public void csCombat(FreeColGameObject attacker,
                         FreeColGameObject defender,
                         List<CombatResult> crs,
                         Random random,
                         ChangeSet cs) throws IllegalStateException {
    	
    	
        CombatModel combatModel = getGame().getCombatModel();
        boolean isAttack = combatModel.combatIsAttack(attacker, defender);
        boolean isBombard = combatModel.combatIsBombard(attacker, defender);
        Unit attackerUnit = null;
        Settlement attackerSettlement = null;
        Tile attackerTile = null;
        Unit defenderUnit = null;
        ServerPlayer defenderPlayer = null;
        Tile defenderTile = null;
        if (isAttack) {
            attackerUnit = (Unit) attacker;
            attackerTile = attackerUnit.getTile();
            defenderUnit = (Unit) defender;
            defenderPlayer = (ServerPlayer) defenderUnit.getOwner();
            defenderTile = defenderUnit.getTile();
            boolean bombard = attackerUnit.hasAbility(Ability.BOMBARD);
            cs.addAttribute(See.only(this), "sound",
                (attackerUnit.isNaval()) ? "sound.attack.naval"
                : (bombard) ? "sound.attack.artillery"
                : (attackerUnit.isMounted()) ? "sound.attack.mounted"
                : "sound.attack.foot");
            if (attackerUnit.getOwner().isIndian()
                && defenderPlayer.isEuropean()
                && defenderUnit.getLocation().getColony() != null
                && !defenderPlayer.atWarWith(attackerUnit.getOwner())) {
                StringTemplate attackerNation
                    = attackerUnit.getApparentOwnerName();
                Colony colony = defenderUnit.getLocation().getColony();
                cs.addMessage(See.only(defenderPlayer),
                    new ModelMessage(ModelMessage.MessageType.COMBAT_RESULT,
                        "model.unit.indianSurprise", colony)
                    .addStringTemplate("%nation%", attackerNation)
                    .addName("%colony%", colony.getName()));
            }
        } else if (isBombard) {
            attackerSettlement = (Settlement) attacker;
            attackerTile = attackerSettlement.getTile();
            defenderUnit = (Unit) defender;
            defenderPlayer = (ServerPlayer) defenderUnit.getOwner();
            defenderTile = defenderUnit.getTile();
            cs.addAttribute(See.only(this), "sound", "sound.attack.bombard");
        } else {
            throw new IllegalStateException("Bogus combat");
        }

        // If the combat results were not specified (usually the case),
        // query the combat model.
        if (crs == null) {
            crs = combatModel.generateAttackResult(random, attacker, defender);
        }
        if (crs.isEmpty()) {
            throw new IllegalStateException("empty attack result");
        }
        // Extract main result, insisting it is one of the fundamental cases,
        // and add the animation.
        // Set vis so that loser always sees things.
        // TODO: Bombard animations
        See vis; // Visibility that insists on the loser seeing the result.
        CombatResult result = crs.remove(0);
        switch (result) {
        case NO_RESULT:
            vis = See.perhaps();
            break; // Do not animate if there is no result.
        case WIN:
            vis = See.perhaps().always(defenderPlayer);
            if (isAttack) {
                if (attackerTile == null || defenderTile == null
                    || attackerTile == defenderTile
                    || !attackerTile.isAdjacent(defenderTile)) {
                    logger.warning("Bogus attack from " + attackerTile
                        + " to " + defenderTile
                        + "\n" + FreeColDebugger.stackTraceToString());
                } else {
                    cs.addAttack(vis, attackerUnit, defenderUnit,
                                 attackerTile, defenderTile, true);
                }
            }
            break;
        case LOSE:
            vis = See.perhaps().always(this);
            if (isAttack) {
                if (attackerTile == null || defenderTile == null
                    || attackerTile == defenderTile
                    || !attackerTile.isAdjacent(defenderTile)) {
                    logger.warning("Bogus attack from " + attackerTile
                        + " to " + defenderTile
                        + "\n" + FreeColDebugger.stackTraceToString());
                } else {
                    cs.addAttack(vis, attackerUnit, defenderUnit,
                                 attackerTile, defenderTile, false);
                }
            }
            break;
        default:
            throw new IllegalStateException("generateAttackResult returned: "
                                            + result);
        }
        // Now process the details.
        boolean attackerTileDirty = false;
        boolean defenderTileDirty = false;
        boolean moveAttacker = false;
        boolean burnedNativeCapital = false;
        Settlement settlement = defenderTile.getSettlement();
        Colony colony = defenderTile.getColony();
        IndianSettlement natives = (settlement instanceof IndianSettlement)
            ? (IndianSettlement) settlement
            : null;
        int attackerTension = 0;
        int defenderTension = 0;
        for (CombatResult cr : crs) {
            boolean ok;
            switch (cr) {
            case AUTOEQUIP_UNIT:
                ok = isAttack && settlement != null;
                if (ok) {
                    csAutoequipUnit(defenderUnit, settlement, cs);
                }
                break;
            case BURN_MISSIONS:
                ok = isAttack && result == CombatResult.WIN
                    && natives != null
                    && isEuropean() && defenderPlayer.isIndian();
                if (ok) {
                    defenderTileDirty |= natives.getMissionary(this) != null;
                    csBurnMissions(attackerUnit, natives, cs);
                }
                break;
            case CAPTURE_AUTOEQUIP:
                ok = isAttack && result == CombatResult.WIN
                    && settlement != null
                    && defenderPlayer.isEuropean();
                if (ok) {
                    csCaptureAutoEquip(attackerUnit, defenderUnit, cs);
                    attackerTileDirty = defenderTileDirty = true;
                }
                break;
            case CAPTURE_COLONY:
                ok = isAttack && result == CombatResult.WIN
                    && colony != null
                    && isEuropean() && defenderPlayer.isEuropean();
                if (ok) {
                    csCaptureColony(attackerUnit, colony, random, cs);
                    attackerTileDirty = defenderTileDirty = false;
                    moveAttacker = true;
                    defenderTension += Tension.TENSION_ADD_MAJOR;
                }
                break;
            case CAPTURE_CONVERT:
                ok = isAttack && result == CombatResult.WIN
                    && natives != null
                    && isEuropean() && defenderPlayer.isIndian();
                if (ok) {
                    csCaptureConvert(attackerUnit, natives, random, cs);
                    attackerTileDirty = true;
                }
                break;
            case CAPTURE_EQUIP:
                ok = isAttack && result != CombatResult.NO_RESULT;
                if (ok) {
                    if (result == CombatResult.WIN) {
                        csCaptureEquip(attackerUnit, defenderUnit, cs);
                    } else {
                        csCaptureEquip(defenderUnit, attackerUnit, cs);
                    }
                    attackerTileDirty = defenderTileDirty = true;
                }
                break;
            case CAPTURE_UNIT:
                ok = isAttack && result != CombatResult.NO_RESULT;
                if (ok) {
                    if (result == CombatResult.WIN) {
                        csCaptureUnit(attackerUnit, defenderUnit, cs);
                    } else {
                        csCaptureUnit(defenderUnit, attackerUnit, cs);
                    }
                    attackerTileDirty = defenderTileDirty = true;
                }
                break;
            case DAMAGE_COLONY_SHIPS:
                ok = isAttack && result == CombatResult.WIN
                    && colony != null;
                if (ok) {
                    csDamageColonyShips(attackerUnit, colony, cs);
                    defenderTileDirty = true;
                }
                break;
            case DAMAGE_SHIP_ATTACK:
                ok = isAttack && result != CombatResult.NO_RESULT
                    && ((result == CombatResult.WIN) ? defenderUnit
                        : attackerUnit).isNaval();
                if (ok) {
                    if (result == CombatResult.WIN) {
                        csDamageShipAttack(attackerUnit, defenderUnit, cs);
                        defenderTileDirty = true;
                    } else {
                        csDamageShipAttack(defenderUnit, attackerUnit, cs);
                        attackerTileDirty = true;
                    }
                }
                break;
            case DAMAGE_SHIP_BOMBARD:
                ok = isBombard && result == CombatResult.WIN
                    && defenderUnit.isNaval();
                if (ok) {
                    csDamageShipBombard(attackerSettlement, defenderUnit, cs);
                    defenderTileDirty = true;
                }
                break;
            case DEMOTE_UNIT:
                ok = isAttack && result != CombatResult.NO_RESULT;
                if (ok) {
                    if (result == CombatResult.WIN) {
                        csDemoteUnit(attackerUnit, defenderUnit, cs);
                        defenderTileDirty = true;
                    } else {
                        csDemoteUnit(defenderUnit, attackerUnit, cs);
                        attackerTileDirty = true;
                    }
                }
                break;
            case DESTROY_COLONY:
                ok = isAttack && result == CombatResult.WIN
                    && colony != null
                    && isIndian() && defenderPlayer.isEuropean();
                if (ok) {
                    csDestroyColony(attackerUnit, colony, random, cs);
                    attackerTileDirty = defenderTileDirty = true;
                    moveAttacker = true;
                    attackerTension -= Tension.TENSION_ADD_NORMAL;
                    defenderTension += Tension.TENSION_ADD_MAJOR;
                }
                break;
            case DESTROY_SETTLEMENT:
                ok = isAttack && result == CombatResult.WIN
                    && natives != null
                    && defenderPlayer.isIndian();
                if (ok) {
                    burnedNativeCapital = settlement.isCapital();
                    csDestroySettlement(attackerUnit, natives, random, cs);
                    attackerTileDirty = defenderTileDirty = true;
                    moveAttacker = true;
                    attackerTension -= Tension.TENSION_ADD_NORMAL;
                    if (!burnedNativeCapital) {
                        defenderTension += Tension.TENSION_ADD_MAJOR;
                    }
                }
                break;
            case EVADE_ATTACK:
                ok = isAttack && result == CombatResult.NO_RESULT
                    && defenderUnit.isNaval();
                if (ok) {
                    csEvadeAttack(attackerUnit, defenderUnit, cs);
                }
                break;
            case EVADE_BOMBARD:
                ok = isBombard && result == CombatResult.NO_RESULT
                    && defenderUnit.isNaval();
                if (ok) {
                    csEvadeBombard(attackerSettlement, defenderUnit, cs);
                }
                break;
            case LOOT_SHIP:
                ok = isAttack && result != CombatResult.NO_RESULT
                    && attackerUnit.isNaval() && defenderUnit.isNaval();
                if (ok) {
                    if (result == CombatResult.WIN) {
                        csLootShip(attackerUnit, defenderUnit, cs);
                    } else {
                        csLootShip(defenderUnit, attackerUnit, cs);
                    }
                }
                break;
            case LOSE_AUTOEQUIP:
                ok = isAttack && result == CombatResult.WIN
                    && settlement != null
                    && defenderPlayer.isEuropean();
                if (ok) {
                    csLoseAutoEquip(attackerUnit, defenderUnit, cs);
                    defenderTileDirty = true;
                }
                break;
            case LOSE_EQUIP:
                ok = isAttack && result != CombatResult.NO_RESULT;
                if (ok) {
                    if (result == CombatResult.WIN) {
                        csLoseEquip(attackerUnit, defenderUnit, cs);
                        defenderTileDirty = true;
                    } else {
                        csLoseEquip(defenderUnit, attackerUnit, cs);
                        attackerTileDirty = true;
                    }
                }
                break;
            case PILLAGE_COLONY:
                ok = isAttack && result == CombatResult.WIN
                    && colony != null
                    && isIndian() && defenderPlayer.isEuropean();
                if (ok) {
                    csPillageColony(attackerUnit, colony, random, cs);
                    defenderTileDirty = true;
                    attackerTension -= Tension.TENSION_ADD_NORMAL;
                }
                break;
            case PROMOTE_UNIT:
                ok = isAttack && result != CombatResult.NO_RESULT;
                if (ok) {
                    if (result == CombatResult.WIN) {
                        csPromoteUnit(attackerUnit, defenderUnit, cs);
                        attackerTileDirty = true;
                    } else {
                        csPromoteUnit(defenderUnit, attackerUnit, cs);
                        defenderTileDirty = true;
                    }
                }
                break;
            case SINK_COLONY_SHIPS:
                ok = isAttack && result == CombatResult.WIN
                    && colony != null;
                if (ok) {
                    csSinkColonyShips(attackerUnit, colony, cs);
                    defenderTileDirty = true;
                }
                break;
            case SINK_SHIP_ATTACK:
                ok = isAttack && result != CombatResult.NO_RESULT
                    && ((result == CombatResult.WIN) ? defenderUnit
                        : attackerUnit).isNaval();
                if (ok) {
                    if (result == CombatResult.WIN) {
                        csSinkShipAttack(attackerUnit, defenderUnit, cs);
                        defenderTileDirty = true;
                    } else {
                        csSinkShipAttack(defenderUnit, attackerUnit, cs);
                        attackerTileDirty = true;
                    }
                }
                break;
            case SINK_SHIP_BOMBARD:
                ok = isBombard && result == CombatResult.WIN
                    && defenderUnit.isNaval();
                if (ok) {
                    csSinkShipBombard(attackerSettlement, defenderUnit, cs);
                    defenderTileDirty = true;
                }
                break;
            case SLAUGHTER_UNIT:
                ok = isAttack && result != CombatResult.NO_RESULT;
                if (ok) {
                    if (result == CombatResult.WIN) {
                        csSlaughterUnit(attackerUnit, defenderUnit, cs);
                        defenderTileDirty = true;
                        attackerTension -= Tension.TENSION_ADD_NORMAL;
                        defenderTension += getSlaughterTension(defenderUnit);
                    } else {
                        csSlaughterUnit(defenderUnit, attackerUnit, cs);
                        attackerTileDirty = true;
                        attackerTension += getSlaughterTension(attackerUnit);
                        defenderTension -= Tension.TENSION_ADD_NORMAL;
                    }
                }
                break;
            default:
                ok = false;
                break;
            }
            if (!ok) {
                throw new IllegalStateException("Attack (result=" + result
                                                + ") has bogus subresult: "
                                                + cr);
            }
        }

        // Handle stance and tension.
        // - Privateers do not provoke stance changes but can set the
        //     attackedByPrivateers flag
        // - Attacks among Europeans imply war
        // - Burning of a native capital results in surrender
        // - Other attacks involving natives do not imply war, but
        //     changes in Tension can drive Stance, however this is
        //     decided by the native AI in their turn so just adjust tension.
        if (attacker.hasAbility(Ability.PIRACY)) {
            if (!defenderPlayer.getAttackedByPrivateers()) {
                defenderPlayer.setAttackedByPrivateers(true);
                cs.addPartial(See.only(defenderPlayer), defenderPlayer,
                              "attackedByPrivateers");
            }
        } else if (defender.hasAbility(Ability.PIRACY)) {
            ; // do nothing
        } else if (burnedNativeCapital) {
            defenderPlayer.getTension(this).setValue(Tension.SURRENDERED);
            cs.add(See.perhaps().always(this), defenderPlayer); // TODO: just the tension
            csChangeStance(Stance.PEACE, defenderPlayer, true, cs);
            for (IndianSettlement is : defenderPlayer.getIndianSettlements()) {
                if (is.hasContacted(this)) {
                    is.getAlarm(this).setValue(Tension.SURRENDERED);
                    // Only update attacker with settlements that have
                    // been seen, as contact can occur with its members.
                    if (is.getTile().isExploredBy(this)) {
                        cs.add(See.perhaps().always(this), is);
                    } else {
                        cs.add(See.only(defenderPlayer), is);
                    }
                }
            }
        } else if (isEuropean() && defenderPlayer.isEuropean()) {
            csChangeStance(Stance.WAR, defenderPlayer, true, cs);
        } else { // At least one player is non-European
            if (isEuropean()) {
                csChangeStance(Stance.WAR, defenderPlayer, true, cs);
            } else if (isIndian()) {
                if (result == CombatResult.WIN) {
                    attackerTension -= Tension.TENSION_ADD_MINOR;
                } else if (result == CombatResult.LOSE) {
                    attackerTension += Tension.TENSION_ADD_MINOR;
                }
            }
            if (defenderPlayer.isEuropean()) {
                defenderPlayer.csChangeStance(Stance.WAR, this, true, cs);
            } else if (defenderPlayer.isIndian()) {
                if (result == CombatResult.WIN) {
                    defenderTension += Tension.TENSION_ADD_MINOR;
                } else if (result == CombatResult.LOSE) {
                    defenderTension -= Tension.TENSION_ADD_MINOR;
                }
            }
            if (attackerTension != 0) {
                cs.add(See.only(null).perhaps(defenderPlayer),
                       modifyTension(defenderPlayer, attackerTension));
            }
            if (defenderTension != 0) {
                cs.add(See.only(null).perhaps(this),
                       defenderPlayer.modifyTension(this, defenderTension));
            }
        }

        // Move the attacker if required.
        if (moveAttacker) {
            attackerUnit.setMovesLeft(attackerUnit.getInitialMovesLeft());
            ((ServerUnit) attackerUnit).csMove(defenderTile, random, cs);
            attackerUnit.setMovesLeft(0);
            // Move adds in updates for the tiles, but...
            attackerTileDirty = defenderTileDirty = false;
            // ...with visibility of perhaps().
            // Thus the defender might see the change,
            // but because its settlement is gone it also might not.
            // So add in another defender-specific update.
            // The worst that can happen is a duplicate update.
            cs.add(See.only(defenderPlayer), defenderTile);
        } else if (isAttack) {
            // The Revenger unit can attack multiple times, so spend
            // at least the eventual cost of moving to the tile.
            // Other units consume the entire move.
            if (attacker.hasAbility("model.ability.multipleAttacks")) {
                int movecost = attackerUnit.getMoveCost(defenderTile);
                attackerUnit.setMovesLeft(attackerUnit.getMovesLeft()
                                          - movecost);
            } else {
                attackerUnit.setMovesLeft(0);
            }
            if (!attackerTileDirty) {
                cs.addPartial(See.only(this), attacker, "movesLeft");
            }
        }

        // Make sure we always update the attacker and defender tile
        // if it is not already done yet.
        if (attackerTileDirty) cs.add(vis, attackerTile);
        if (defenderTileDirty) cs.add(vis, defenderTile);
    }//end method
    
 
    private int getSlaughterTension(Unit loser) {
        // Tension rises faster when units die.
        Settlement settlement = loser.getSettlement();
        if (settlement != null) {
            if (settlement instanceof IndianSettlement) {
                return (((IndianSettlement) settlement).isCapital())
                    ? Tension.TENSION_ADD_CAPITAL_ATTACKED
                    : Tension.TENSION_ADD_SETTLEMENT_ATTACKED;
            } else {
                return Tension.TENSION_ADD_NORMAL;
            }
        } else { // attack in the open
            return (loser.getIndianSettlement() != null)
                ? Tension.TENSION_ADD_UNIT_DESTROYED
                : Tension.TENSION_ADD_MINOR;
        }
    }

    
    
    
    //added to override inherited methods
	@Override
	public String getServerXMLElementTagName() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void csNewTurn(Random random, ChangeSet cs) {
		// TODO Auto-generated method stub
		
	}
}
