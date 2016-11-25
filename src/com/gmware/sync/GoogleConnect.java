package com.gmware.sync;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.google.api.services.calendar.model.Events;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GoogleConnect {

    public static void main(final String[] args) throws IOException {
        connectGoogleCalendar();
    }

    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "Google Calendar Synchronization";

    /**
     * Directory to store user credentials for this application.
     */
    private static final File DATA_STORE_DIR = new File(".");

    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this quickstart.
     * <p>
     * If modifying these scopes, delete your previously saved credentials at
     * ~/.credentials/calendar-java-quickstart
     */
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    static ArrayList<CalendarEvent> getEvents(final Calendar service)
        throws IOException, ParseException {
        final DateTime now = new DateTime(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(15));
        final Events events = service.events().list("primary")
            // .setMaxResults(10)
            .setTimeMin(now)
            .setOrderBy("startTime")
            .setSingleEvents(true)
            .execute();
        final List<Event> items = events.getItems();
        final ArrayList<CalendarEvent> out = new ArrayList<>();
        for (final Event item : items) {
            final CalendarEvent calendarEvent = new CalendarEvent();
            calendarEvent.subject = item.getSummary();
            if (!calendarEvent.subject.endsWith(Synchronizer.SYNCHRONIZER_TAG)) {
                continue;
            }
            calendarEvent.start = CalendarEvent.dateFormat.get()
                .format(new Date(item.getStart().getDateTime().getValue()));
            calendarEvent.end = CalendarEvent.dateFormat.get()
                .format(new Date(item.getEnd().getDateTime().getValue()));
            calendarEvent.location = item.getLocation();
            calendarEvent.uid = item.getId();
            if (!calendarEvent.isActual()) {
                continue;
            }
            out.add(calendarEvent);
        }
        return out;
    }

    static void deleteEvent(final Calendar service, final CalendarEvent calendarEvent)
        throws IOException {
        service.events().delete("primary", calendarEvent.uid).execute();
        System.out.println("Event deleted " + calendarEvent.subject);
    }

    static void addEvent(final Calendar service, final CalendarEvent calendarEvent)
        throws IOException {
        Event event = new Event()
            .setSummary(calendarEvent.subject)
            .setLocation(calendarEvent.location)
            .setDescription("[" + calendarEvent.author + "] " + calendarEvent.invited + " "
                + calendarEvent.modified);

        final DateTime startDateTime = new DateTime(
            calendarEvent.start.replace(' ', 'T').replace('.', '-') + ":00+06:00");
        final String timeZone = "UTC";//"Asia/Omsk";
        EventDateTime start = new EventDateTime()
            .setDateTime(startDateTime)
            .setTimeZone(timeZone);
        event.setStart(start);

        final DateTime endDateTime = new DateTime(
            calendarEvent.end.replace(' ', 'T').replace('.', '-') + ":00+06:00");
        EventDateTime end = new EventDateTime()
            .setDateTime(endDateTime)
            .setTimeZone(timeZone);
        event.setEnd(end);

        // EventAttendee[] attendees = new EventAttendee[]{
        //         new EventAttendee().setEmail("kira@aistmail.com"),
        //         new EventAttendee().setEmail("darwin@aistmail.com"),
        // };
        // event.setAttendees(Arrays.asList(attendees));

        if (Synchronizer.popupNotificationDisplay != null) {
            final EventReminder[] reminderOverrides = new EventReminder[Synchronizer.popupNotificationDisplay.length];
            for (int i = 0; i < Synchronizer.popupNotificationDisplay.length; i++) {
                reminderOverrides[i] = new EventReminder().setMethod("popup")
                    .setMinutes(Synchronizer.popupNotificationDisplay[i]);
            }
            Event.Reminders reminders = new Event.Reminders()
                .setUseDefault(false)
                .setOverrides(Arrays.asList(reminderOverrides));
            event.setReminders(reminders);
        }

        event = service.events().insert("primary", event).execute();
        System.out.printf("Event created: %s\n", event.getHtmlLink());
    }

    static Calendar connectGoogleCalendar() throws IOException {
        // Load client secrets.
        // final InputStream in = GoogleConnect.class.getResourceAsStream("/client_secret.json");
        final InputStream in = new FileInputStream("client_secret.json");
        final GoogleClientSecrets clientSecrets = GoogleClientSecrets
            .load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        final GoogleAuthorizationCodeFlow flow =
            new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();
        final Credential credential = new AuthorizationCodeInstalledApp(flow,
            new LocalServerReceiver()).authorize("user");
        System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return new Calendar
            .Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }
}
