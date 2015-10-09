/*
 * Copyright 2015 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import java.net.SocketAddress;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.PatternBuilder;
import org.traccar.model.Event;
import org.traccar.model.Position;

public class GoSafeProtocolDecoder extends BaseProtocolDecoder {

    public GoSafeProtocolDecoder(GoSafeProtocol protocol) {
        super(protocol);
    }

    private static final Pattern PATTERN = new PatternBuilder()
            .txt("*GS")                          // header
            .num("d+,")                          // protocol version
            .num("(d+),")                        // imei
            .num("(dd)(dd)(dd)")                 // time
            .num("(dd)(dd)(dd),")                // date
            .xpr("(.*)#?")                       // data
            .compile();

    private static final Pattern PATTERN_ITEM = new PatternBuilder()
            .num("(x+)?,")                       // event
            .groupBegin()
            .txt("SYS:")
            .nxt(",")
            .groupEnd(true)
            .groupBegin()
            .txt("GPS:")
            .xpr("([AV]);")                      // validity
            .num("(d+);")                        // satellites
            .num("([NS])(d+.d+);")               // latitude
            .num("([EW])(d+.d+);")               // longitude
            .num("(d+);")                        // speed
            .num("(d+);")                        // course
            .num("(d+);")                        // altitude
            .num("(d+.d+)")                      // hdop
            .opn(";d+.d+")                       // vdop
            .xpr(",?")
            .groupEnd(false)
            .groupBegin()
            .txt("COT:")
            .num("(d+);")                        // odometer
            .not("(d+):d+:d+")                   // engine hours
            .xpr(",?")
            .groupEnd(true)
            .groupBegin()
            .txt("ADC:")
            .num("(d+.d+);")                     // power
            .num("(d+.d+)")                      // battery
            .xpr(",?")
            .groupEnd(true)
            .groupBegin()
            .txt("DTT:")
            .not(",")
            .xpr(",?")
            .groupEnd(true)
            .groupBegin()
            .txt("ETD:")
            .not(",")
            .xpr(",?")
            .groupEnd(true)
            .groupBegin()
            .txt("OBD:")
            .not(",")
            .xpr(",?")
            .groupEnd(true)
            .groupBegin()
            .txt("FUL:")
            .not(",")
            .xpr(",?")
            .groupEnd(true)
            .groupBegin()
            .txt("TRU:")
            .not(",")
            .xpr(",?")
            .groupEnd(true)
            .compile();

    private Position decodePosition(Matcher parser, Date time) {

        Position position = new Position();
        position.setDeviceId(getDeviceId());
        position.setTime(time);

        Integer index = 1;

        position.set(Event.KEY_EVENT, parser.group(index++));

        // Validity
        position.setValid(parser.group(index++).equals("A"));
        position.set(Event.KEY_SATELLITES, parser.group(index++));

        // Latitude
        String hemisphere = parser.group(index++);
        Double latitude = Double.parseDouble(parser.group(index++));
        if (hemisphere.equals("S")) latitude = -latitude;
        position.setLatitude(latitude);

        // Longitude
        hemisphere = parser.group(index++);
        Double longitude = Double.parseDouble(parser.group(index++));
        if (hemisphere.equals("W")) longitude = -longitude;
        position.setLongitude(longitude);

        // Other
        position.setSpeed(Double.parseDouble(parser.group(index++)));
        position.setCourse(Double.parseDouble(parser.group(index++)));
        position.setAltitude(Double.parseDouble(parser.group(index++)));
        position.set(Event.KEY_HDOP, parser.group(index++));

        position.set(Event.KEY_ODOMETER, parser.group(index++));
        position.set("hours", parser.group(index++));

        return position;
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg)
            throws Exception {

        String sentence = (String) msg;

        if (channel != null) {
            channel.write("1234");
        }

        Matcher parser = PATTERN.matcher(sentence);
        if (!parser.matches()) {
            return null;
        }

        Integer index = 1;

        if (!identify(parser.group(index++), channel, remoteAddress)) {
            return null;
        }

        Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        time.clear();
        time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parser.group(index++)));
        time.set(Calendar.MINUTE, Integer.parseInt(parser.group(index++)));
        time.set(Calendar.SECOND, Integer.parseInt(parser.group(index++)));
        time.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parser.group(index++)));
        time.set(Calendar.MONTH, Integer.parseInt(parser.group(index++)) - 1);
        time.set(Calendar.YEAR, 2000 + Integer.parseInt(parser.group(index++)));

        List<Position> positions = new LinkedList<>();
        Matcher itemParser = PATTERN_ITEM.matcher(parser.group(index++));

        while (itemParser.find()) {
            positions.add(decodePosition(itemParser, time.getTime()));
        }

        return positions;
    }

}
