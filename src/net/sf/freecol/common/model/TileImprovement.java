/**
 *  Copyright (C) 2002-2012   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.model;

import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import net.sf.freecol.common.model.Map.Direction;


/**
 * Represents a tile improvement, such as a river or road.
 */
public class TileImprovement extends TileItem implements Named {

    private static Logger logger = Logger.getLogger(TileImprovement.class.getName());

    private TileImprovementType type;
    private int turnsToComplete;

    /**
     * Default is type.getMagnitude(), but this will override.
     */
    private int magnitude;

    /**
     * River magnitudes
     */
    public static final int NO_RIVER = 0;
    public static final int SMALL_RIVER = 1;
    public static final int LARGE_RIVER = 2;
    public static final int FJORD_RIVER = 3;


    /**
     * To store the style of multi-image TileImprovements (eg. rivers)
     * Rivers have 4 directions {NE=1, SE=3, SW=9, NW=27}, and 3 levels (see above)
     * @see Map
     * @see net.sf.freecol.server.generator.River
     */
    private TileImprovementStyle style;

    /**
     * Whether this is a virtual improvement granted by some structure
     * on the tile (a Colony, for example). Virtual improvements will
     * be removed along with the structure that granted them.
     */
    private boolean virtual;

    // ------------------------------------------------------------ constructor

    /**
     * Creates a standard <code>TileImprovement</code>-instance.
     *
     * This constructor asserts that the game, tile and type are valid.
     *
     * @param game The <code>Game</code> in which this object belongs.
     * @param tile The <code>Tile</code> on which this object sits.
     * @param type The <code>TileImprovementType</code> of this TileImprovement.
     */
    public TileImprovement(Game game, Tile tile, TileImprovementType type) {
        super(game, tile);
        if (type == null) {
            throw new IllegalArgumentException("Parameter 'type' must not be 'null'.");
        }
        this.type = type;
        if (!type.isNatural()) {
            this.turnsToComplete = tile.getType().getBasicWorkTurns() + type.getAddWorkTurns();
        }
        this.magnitude = type.getMagnitude();
    }

    public TileImprovement(Game game, XMLStreamReader in) throws XMLStreamException {
        super(game, in);
        readFromXML(in);
    }

    /**
     * Initiates a new <code>TileImprovement</code> with the given ID. The object
     * should later be initialized by calling either
     * {@link #readFromXML(XMLStreamReader)} or
     * {@link #readFromXMLElement(Element)}.
     *
     * @param game The <code>Game</code> in which this object belong.
     * @param id The unique identifier for this object.
     */
    public TileImprovement(Game game, String id) {
        super(game, id);
    }

    // ------------------------------------------------------------ retrieval methods

    public TileImprovementType getType() {
        return type;
    }

    public int getMagnitude() {
        return magnitude;
    }

    public void setMagnitude(int magnitude) {
        this.magnitude = magnitude;
    }

    /**
     * Get the <code>Virtual</code> value.
     *
     * @return a <code>boolean</code> value
     */
    public final boolean isVirtual() {
        return virtual;
    }

    /**
     * Set the <code>Virtual</code> value.
     *
     * @param newVirtual The new Virtual value.
     */
    public final void setVirtual(final boolean newVirtual) {
        this.virtual = newVirtual;
    }

    /**
     * Is this <code>TileImprovement</code> a road?
     * @return a <code>boolean</code> value
     */
    public boolean isRoad() {
        return getType().getId().equals("model.improvement.road");
    }

    /**
     * Is this <code>TileImprovement</code> a river?
     * @return a <code>boolean</code> value
     */
    public boolean isRiver() {
        return getType().getId().equals("model.improvement.river");
    }

    public String getNameKey() {
        return getType().getNameKey();
    }

    /**
     * Returns a textual representation of this object.
     * @return A <code>String</code> of either:
     * <ol>
     * <li>NAME (#TURNS turns left) (eg. Road (2 turns left) ) if it is under construction
     * <li>NAME (eg. Road) if it is complete
     * </ol>
     */
    public String toString() {
        if (turnsToComplete > 0) {
            return getType().getId() + " (" + Integer.toString(turnsToComplete) + " turns left)";
        } else {
            return getType().getId();
        }
    }

    /**
     * @return the current turns to completion.
     */
    public int getTurnsToComplete() {
        return turnsToComplete;
    }

    /**
     * Update the turns required to complete the improvement.
     *
     * @param turns an <code>int</code> value
     */
    public void setTurnsToComplete(int turns) {
        turnsToComplete = turns;
    }

    /**
     * Get the <code>ZIndex</code> value.
     *
     * @return an <code>int</code> value
     */
    public final int getZIndex() {
        return type.getZIndex();
    }

    public boolean isComplete() {
        return turnsToComplete <= 0;
    }

    public EquipmentType getExpendedEquipmentType() {
        return type.getExpendedEquipmentType();
    }

    public int getExpendedAmount() {
        return type.getExpendedAmount();
    }

    /**
     * Returns the bonus (if any).
     * @param goodsType a <code>GoodsType</code> value
     * @return an <code>int</code> value
     */
    public int getBonus(GoodsType goodsType) {
        if (!isComplete()) {
            return 0;
        }
        return type.getBonus(goodsType);
    }

    /**
     * Returns the bonus Modifier (if any).
     * @param goodsType a <code>GoodsType</code> value
     * @return a <code>Modifier</code> value
     */
    public Modifier getProductionModifier(GoodsType goodsType) {
        if (!isComplete()) {
            return null;
        }
        return type.getProductionModifier(goodsType);
    }

    /**
     * Calculates the movement cost on the basis of connected tile
     * improvements.
     *
     * @param fromTile a <code>Tile</code> value
     * @param targetTile a <code>Tile</code> value
     * @param moveCost Original movement cost
     * @return The movement cost after any change
     */
    public int getMoveCost(Tile fromTile, Tile targetTile, int moveCost) {
        if (isComplete()) {
            if (style == null) {
                // implicitly connected to all neighbouring tiles
                return type.getMoveCost(moveCost);
            } else {
                Direction direction = targetTile.getMap().getDirection(targetTile, fromTile);
                // TODO: fix this properly, roads are getting bogus styles
                boolean connected = (isRiver())
                    ? style != null && style.isConnectedTo(direction)
                    : (isRoad())
                    ? fromTile.hasRoad() && targetTile.hasRoad()
                    : false;
                if (connected) return type.getMoveCost(moveCost);
            }
        }
        return moveCost;
    }

    /**
     * Returns any change of TileType
     * @return The new TileType.
     */
    public TileType getChange(TileType tileType) {
        if (!isComplete()) {
            return null;
        }
        return type.getChange(tileType);
    }

    /**
     * Returns the Style of this Improvement - used for Rivers
     * @return The style
     */
    public TileImprovementStyle getStyle() {
        return style;
    }

    /**
     * Sets the Style of this Improvement - used for Rivers
     * @param style The style
     */
    public void setStyle(TileImprovementStyle style) {
        this.style = style;
    }

    /**
     * Returns <code>true</code> if this TileImprovement is connected
     * to a similar TileImprovement on the given tile.
     *
     * @param direction a <code>Direction</code> value
     * @return a <code>boolean</code> value
     */
    public boolean isConnectedTo(Direction direction) {
        return style == null ? false : style.isConnectedTo(direction);
    }

    /**
     * Checks if a given worker can work at this Improvement
     */
    public boolean isWorkerAllowed(Unit unit) {
        if (unit == null) {
            return false;
        }
        if (isComplete()) {
            return false;
        }
        return type.isWorkerAllowed(unit);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isTileTypeAllowed(TileType tileType) {
        return type.isTileTypeAllowed(tileType);
    }


    /**
     * This method writes an XML-representation of this object to the given
     * stream.
     *
     * <br>
     * <br>
     *
     * Only attributes visible to the given <code>Player</code> will be added
     * to that representation if <code>showAll</code> is set to
     * <code>false</code>.
     *
     * @param out The target stream.
     * @param player The <code>Player</code> this XML-representation should be
     *            made for, or <code>null</code> if
     *            <code>showAll == true</code>.
     * @param showAll Only attributes visible to <code>player</code> will be
     *            added to the representation if <code>showAll</code> is set
     *            to <i>false</i>.
     * @param toSavedGame If <code>true</code> then information that is only
     *            needed when saving a game is added.
     * @throws XMLStreamException if there are any problems writing to the
     *             stream.
     */
    @Override
    protected void toXMLImpl(XMLStreamWriter out, Player player,
                             boolean showAll, boolean toSavedGame)
        throws XMLStreamException {
        // Start element:
        out.writeStartElement(getXMLElementTagName());

        // Add attributes:
        out.writeAttribute(ID_ATTRIBUTE, getId());
        out.writeAttribute("tile", getTile().getId());
        out.writeAttribute("type", getType().getId());
        out.writeAttribute("turns", Integer.toString(turnsToComplete));
        out.writeAttribute("magnitude", Integer.toString(magnitude));
        if (style != null) {
            out.writeAttribute("style", style.toString());
        }
        if (virtual) {
            out.writeAttribute("virtual", Boolean.toString(virtual));
        }

        // End element:
        out.writeEndElement();
    }

    /**
     * Initialize this object from an XML-representation of this object.
     *
     * @param in The input stream with the XML.
     * @throws XMLStreamException if a problem was encountered during parsing.
     */
    @Override
    protected void readAttributes(XMLStreamReader in) throws XMLStreamException {
        Game game = getGame();

        setId(in.getAttributeValue(null, ID_ATTRIBUTE));

        tile = game.getFreeColGameObject(in.getAttributeValue(null, "tile"),
                                         Tile.class);
        if (tile == null) {
            tile = new Tile(game, in.getAttributeValue(null, "tile"));
        }

        String str = in.getAttributeValue(null, "type");
        type = getSpecification().getTileImprovementType(str);

        turnsToComplete = Integer.parseInt(in.getAttributeValue(null, "turns"));

        magnitude = Integer.parseInt(in.getAttributeValue(null, "magnitude"));

        style = TileImprovementStyle.getInstance(in.getAttributeValue(null, "style"));

        virtual = getAttribute(in, "virtual", false);

    }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "tileImprovement".
     */
    public static String getXMLElementTagName() {
        return "tileimprovement";
    }
}
