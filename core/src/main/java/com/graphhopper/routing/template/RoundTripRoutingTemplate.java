/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.template;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.AvoidEdgesWeighting;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.tour.SinglePointTour;
import com.graphhopper.routing.util.tour.TourStrategy;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.Translation;
import com.graphhopper.util.shapes.GHPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of calculating a route with one or more round trip (route with identical start and
 * end).
 *
 * @author Peter Karich
 */
public class RoundTripRoutingTemplate implements RoutingTemplate
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RoundTripRoutingTemplate.class);
    private final int maxRetries;
    private final GHRequest ghRequest;
    private final GHResponse ghResponse;
    private PathWrapper altResponse;
    private final LocationIndex locationIndex;
    // result from lookup
    private List<QueryResult> queryResults;
    // result from route
    private List<Path> pathList;

    public RoundTripRoutingTemplate( GHRequest request, GHResponse ghRsp, LocationIndex locationIndex, int maxRetries )
    {
        this.ghRequest = request;
        this.ghResponse = ghRsp;
        this.locationIndex = locationIndex;
        this.maxRetries = maxRetries;
    }

    @Override
    public List<QueryResult> lookup( List<GHPoint> points, FlagEncoder encoder )
    {
        double distanceInMeter = ghRequest.getHints().getDouble("round_trip.distance", 1000);
        long seed = ghRequest.getHints().getLong("round_trip.seed", 0L);
        GHPoint start = ghRequest.getPoints().get(0);

        TourStrategy strategy = new SinglePointTour(new Random(seed), distanceInMeter);
        queryResults = new ArrayList<>(2 + strategy.getNumberOfGeneratedPoints());
        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
        QueryResult startQR = locationIndex.findClosest(start.lat, start.lon, edgeFilter);
        queryResults.add(startQR);

        GHPoint last = points.get(0);
        for (int i = 0; i < strategy.getNumberOfGeneratedPoints(); i++)
        {
            QueryResult result = generateValidPoint(last, strategy.getDistanceForIteration(i), strategy.getHeadingForIteration(i), edgeFilter);
            if (result == null)
            {
                ghResponse.addError(new IllegalStateException("Could not find a valid point after " + maxRetries + " tries, for the point:" + last));
                return Collections.emptyList();
            }
            last = result.getSnappedPoint();
            queryResults.add(result);
        }

        queryResults.add(startQR);
        return queryResults;
    }

    void setQueryResults( List<QueryResult> queryResults )
    {
        this.queryResults = queryResults;
    }

    @Override
    public List<Path> calcPaths( QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts )
    {
        pathList = new ArrayList<>(queryResults.size() - 1);

        AvoidEdgesWeighting avoidPathWeighting = new AvoidEdgesWeighting(algoOpts.getWeighting());
        avoidPathWeighting.setEdgePenaltyFactor(5);
        algoOpts = AlgorithmOptions.start(algoOpts).
                algorithm(AlgorithmOptions.DIJKSTRA_BI).
                weighting(avoidPathWeighting).build();
        long visitedNodesSum = 0L;
        for (int i = 1; i < queryResults.size(); i++)
        {
            RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, algoOpts);
            Path path = algo.calcPath(queryResults.get(i - 1).getClosestNode(), queryResults.get(i).getClosestNode());
            visitedNodesSum += algo.getVisitedNodes();
            pathList.add(path);
            // it is important to avoid previously visited nodes for future paths
            avoidPathWeighting.addEdges(path.calcEdges());
        }

        ghResponse.getHints().put("visited_nodes.sum", visitedNodesSum);
        ghResponse.getHints().put("visited_nodes.average", (float) visitedNodesSum / (queryResults.size() - 1));

        return pathList;
    }

    public void setPaths( List<Path> pathList )
    {
        this.pathList = pathList;
    }

    @Override
    public boolean isReady( PathMerger pathMerger, Translation tr )
    {
        altResponse = new PathWrapper();
        ghResponse.add(altResponse);
        pathMerger.doWork(altResponse, pathList, tr);
        // with potentially retrying, including generating new route points, for now disabled
        return true;
    }

    private QueryResult generateValidPoint( GHPoint from, double distanceInMeters, double heading,
                                            EdgeFilter edgeFilter )
    {
        int tryCount = 0;
        while (true)
        {
            GHPoint generatedPoint = Helper.DIST_EARTH.projectCoordinate(from.getLat(), from.getLon(), distanceInMeters, heading);
            QueryResult qr = locationIndex.findClosest(generatedPoint.getLat(), generatedPoint.getLon(), edgeFilter);
            if (qr.isValid())
                return qr;

            tryCount++;
            distanceInMeters *= 0.95;

            if (tryCount >= maxRetries)
                return null;
        }
    }

    @Override
    public int getMaxRetries()
    {
        // with potentially retrying, including generating new route points, for now disabled
        return 1;
    }
}
