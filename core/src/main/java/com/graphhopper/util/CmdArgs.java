/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
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
package com.graphhopper.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Stores command line options in a map. The capitalization of the key is ignored.
 * <p>
 * @author Peter Karich
 */
public class CmdArgs extends PMap
{

    public CmdArgs()
    {
    }

    public CmdArgs( Map<String, String> map )
    {
        super(map);
    }

    @Override
    public CmdArgs put( String key, Object str )
    {
        super.put(key, str);
        return this;
    }

    /**
     * @param fileStr the file name of config.properties
     * @param systemProperty the property name of the configuration. E.g. -Dgraphhopper.config
     */
    public static CmdArgs readFromConfig( String fileStr, String systemProperty ) throws IOException
    {
        if (systemProperty.startsWith("-D"))
            systemProperty = systemProperty.substring(2);

        String configLocation = System.getProperty(systemProperty);
        if (Helper.isEmpty(configLocation))
            configLocation = fileStr;

        Map<String, String> map = new LinkedHashMap<String, String>();
        Helper.loadProperties(map, new InputStreamReader(new FileInputStream(
                new File(configLocation).getAbsoluteFile()), Helper.UTF_CS));
        CmdArgs args = new CmdArgs();
        args.merge(map);

        // overwrite with system settings
        Properties props = System.getProperties();
        for (Entry<Object, Object> e : props.entrySet())
        {
            String k = ((String) e.getKey());
            String v = ((String) e.getValue());
            if (k.startsWith("graphhopper."))
            {
                k = k.substring("graphhopper.".length());
                args.put(k, v);
            }
        }
        return args;
    }

    public static CmdArgs read( String[] args )
    {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (String arg : args)
        {
            int index = arg.indexOf("=");
            if (index <= 0)
            {
                continue;
            }

            String key = arg.substring(0, index);
            if (key.startsWith("-"))
            {
                key = key.substring(1);
            }

            if (key.startsWith("-"))
            {
                key = key.substring(1);
            }

            String value = arg.substring(index + 1);
            map.put(key.toLowerCase(), value);
        }

        return new CmdArgs(map);
    }

    /**
     * Command line configuration overwrites the ones in the config file.
     * <p>
     * @return a new CmdArgs object if necessary.
     */
    public static CmdArgs readFromConfigAndMerge( CmdArgs args, String configKey, String configSysAttr )
    {
        String configVal = args.get(configKey, "");
        if (!Helper.isEmpty(configVal))
        {
            try
            {
                CmdArgs tmp = CmdArgs.readFromConfig(configVal, configSysAttr);
                tmp.merge(args);
                return tmp;
            } catch (Exception ex)
            {
                throw new RuntimeException(ex);
            }
        }
        return args;
    }
}
