/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;

/**
 * Special weighting for bike, uses the experimental API of encoder.getPriority(EdgeIteratorState)
 * <p>
 * @author Peter Karich
 */
public class PriorityWeighting extends FastestWeighting
{
    private final BikeFlagCommonEncoder encoder;

    public PriorityWeighting( BikeFlagCommonEncoder encoder )
    {
        super(encoder);
        this.encoder = encoder;
    }

    @Override
    public double calcWeight( EdgeIteratorState edge, boolean reverse )
    {
        double weight = super.calcWeight(edge, reverse);
        if (Double.isInfinite(weight))
            return Double.POSITIVE_INFINITY;
        return weight * encoder.getPriority(edge.getFlags());
    }
}
