/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.kb.internal.concept;

import ai.grakn.concept.Rule;
import ai.grakn.concept.RuleType;
import ai.grakn.concept.Thing;
import ai.grakn.concept.Type;
import ai.grakn.kb.internal.structure.VertexElement;
import ai.grakn.graql.Pattern;
import ai.grakn.util.Schema;
import org.apache.tinkerpop.gremlin.structure.Direction;

import java.util.stream.Stream;

/**
 * <p>
 *     A rule which defines how implicit knowledge can extracted.
 * </p>
 *
 * <p>
 *     It can behave like any other {@link Thing} but primarily serves as a way of extracting
 *     implicit data from the graph. By defining the LHS (if statment) and RHS (then conclusion) it is possible to
 *     automatically materialise new concepts based on these rules.
 * </p>
 *
 * @author fppt
 *
 */
public class RuleImpl extends ThingImpl<Rule, RuleType> implements Rule {
    RuleImpl(VertexElement vertexElement) {
        super(vertexElement);
    }

    RuleImpl(VertexElement vertexElement, RuleType type, Pattern when, Pattern then) {
        super(vertexElement, type);
        vertex().propertyImmutable(Schema.VertexProperty.RULE_WHEN, when, getWhen(), Pattern::toString);
        vertex().propertyImmutable(Schema.VertexProperty.RULE_THEN, then, getThen(), Pattern::toString);
        vertex().propertyUnique(Schema.VertexProperty.INDEX, generateRuleIndex(type(), when, then));
    }

    /**
     *
     * @return A string representing the left hand side GraQL query.
     */
    @Override
    public Pattern getWhen() {
        return parsePattern(vertex().property(Schema.VertexProperty.RULE_WHEN));
    }

    /**
     *
     * @return A string representing the right hand side GraQL query.
     */
    @Override
    public Pattern getThen() {
        return parsePattern(vertex().property(Schema.VertexProperty.RULE_THEN));
    }

    private Pattern parsePattern(String value){
        if(value == null) {
            return null;
        } else {
            return vertex().tx().graql().parsePattern(value);
        }
    }

    /**
     *
     * @param type The {@link Type} which this {@link Rule} applies to.
     */
    public void addHypothesis(Type type) {
        putEdge(ConceptVertex.from(type), Schema.EdgeLabel.HYPOTHESIS);
    }

    /**
     *
     * @param type The {@link Type} which is the conclusion of this {@link Rule}.
     */
    public void addConclusion(Type type) {
        putEdge(ConceptVertex.from(type), Schema.EdgeLabel.CONCLUSION);
    }

    /**
     *
     * @return A collection of Concept Types that constitute a part of the hypothesis of the rule
     */
    @Override
    public Stream<Type> getHypothesisTypes() {
        return neighbours(Direction.OUT, Schema.EdgeLabel.HYPOTHESIS);
    }

    /**
     *
     * @return A collection of Concept Types that constitute a part of the conclusion of the rule
     */
    @Override
    public Stream<Type> getConclusionTypes() {
        return neighbours(Direction.OUT, Schema.EdgeLabel.CONCLUSION);
    }

    /**
     * Generate the internal hash in order to perform a faster lookups and ensure rules are unique
     */
    static String generateRuleIndex(RuleType type, Pattern when, Pattern then){
        return "RuleType_" + type.getLabel().getValue() + "_LHS:" + when.hashCode() + "_RHS:" + then.hashCode();
    }

    public static RuleImpl from(Rule rule){
        return (RuleImpl) rule;
    }
}
