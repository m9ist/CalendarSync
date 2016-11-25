package com.gmware.sync;

import com.google.api.services.calendar.Calendar;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

public class Synchronizer {
    final static String SYNCHRONIZER_TAG = " [w]";
    static int[] popupNotificationDisplay = null;
    static String msExchangeUser = null;
    static String msExchangeDomain = null;
    static String msExchangePassword = null;

    public static void main(final String[] args) throws Exception {
        System.out.println("---------------------- Start synchronizing " + CalendarEvent.dateFormat.get().format(new Date()));
        init();
        final Calendar service = GoogleConnect.connectGoogleCalendar();
        final ArrayList<CalendarEvent> eventsMS = ConnectMicrosoftExchange.getActualEvents();
        final ArrayList<CalendarEvent> googleEv = GoogleConnect.getEvents(service);
        for (final CalendarEvent event : googleEv) {
            boolean found = false;
            for (int i = 0; i < eventsMS.size(); i++) {
                final CalendarEvent msEvent = eventsMS.get(i);
                if (msEvent.equals(event)) {
                    found = true;
                    eventsMS.remove(i);
                    break;
                }
            }
            if (!found) {
                GoogleConnect.deleteEvent(service, event);
            }
        }
        System.out.println("Found to add " + eventsMS.size() + " events.");
        for (final CalendarEvent events : eventsMS) GoogleConnect.addEvent(service, events);
        System.out.println("---------------------- End synchronizing " + CalendarEvent.dateFormat.get().format(new Date()));
        System.exit(0);
    }

    static void init() throws IOException {
        System.out.println("Start load properties.");
        final File file = new File(Synchronizer.class.getSimpleName() + ".properties");
        if (!file.exists()) {
            System.out.println(file.getAbsoluteFile() + " doesn't exists.");
            System.exit(1);
        }
        final Properties properties = new Properties();
        try (final InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            properties.load(is);
        }
        msExchangeUser = properties.getProperty("msExchangeUser");
        msExchangeDomain = properties.getProperty("msExchangeDomain");
        msExchangePassword = properties.getProperty("msExchangePassword");

        final String s = properties.getProperty("popupNotificationDisplay", "");
        if (s != null && s.trim().length() > 0) {
            final String[] split = s.split("[, ;]+");
            final ArrayList<Integer> pops = new ArrayList<>();
            for (final String val : split) {
                if (val.length() > 0) {
                    pops.add(Integer.parseInt(val));
                }
            }
            popupNotificationDisplay = new int[pops.size()];
            for (int i = 0; i < popupNotificationDisplay.length; i++) {
                popupNotificationDisplay[i] = pops.get(i);
            }
        }
    }
}
