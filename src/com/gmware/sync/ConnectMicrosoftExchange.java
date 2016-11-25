package com.gmware.sync;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.error.ServiceError;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.service.calendar.AppointmentType;
import microsoft.exchange.webservices.data.core.exception.service.local.ServiceLocalException;
import microsoft.exchange.webservices.data.core.exception.service.remote.ServiceResponseException;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.AppointmentSchema;
import microsoft.exchange.webservices.data.core.service.schema.ItemSchema;
import microsoft.exchange.webservices.data.credential.ExchangeCredentials;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.property.definition.PropertyDefinition;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;

public class ConnectMicrosoftExchange {

    public static void main(final String[] args) throws Exception {
        Synchronizer.init();
        getActualEvents();
    }

    static final int MAX_DISTANCE_REPEATED = 90;

    static ArrayList<CalendarEvent> getActualEvents() throws Exception {
        try (final ExchangeService service = new ExchangeService()) {
            final ExchangeCredentials credentials = new WebCredentials(Synchronizer.msExchangeUser,
                Synchronizer.msExchangePassword, Synchronizer.msExchangeDomain);
            service.setCredentials(credentials);
            final URI url = new URI("https://exch2016.sky.net/EWS/Exchange.asmx");
            service.setUrl(url);
            System.out.println(service.getRequestedServerVersion());
            final FindItemsResults<Item> items = service.findItems(WellKnownFolderName.Calendar,
                new SearchFilter.IsGreaterThanOrEqualTo(ItemSchema.DateTimeCreated,
                    new Date(new Date().getTime() - TimeUnit.DAYS.toMillis(MAX_DISTANCE_REPEATED))),
                new ItemView(1_000_000));
            final ArrayList<CalendarEvent> out = new ArrayList<>();
            for (final Item item : items) {
                final Appointment appointment = (Appointment) item;
                if (appointment.getIsCancelled()) {
                    continue;
                }

                final CalendarEvent calendarEvent = createCalendarEvent(appointment);
                if (!calendarEvent
                    .needToProcess(appointment.getAppointmentType() != AppointmentType.Single)) {
                    continue;
                }

                for (final Map.Entry<PropertyDefinition, Object> entry : appointment
                    .getPropertyBag().getProperties().entrySet()) {
                    final String key = entry.getKey().getXmlElement();
                    final Object value = entry.getValue();
                    if ("DisplayTo".equals(key)) {
                        calendarEvent.invited = (String) value;
                        break;
                    }
                }
                fillRepeatedEvents(appointment, service, calendarEvent, out);
                if (calendarEvent.isActual()) {
                    out.add(calendarEvent);
                }
            }
            return out;
        }
    }

    private static CalendarEvent createCalendarEvent(final Appointment appointment)
        throws ServiceLocalException {
        final CalendarEvent calendarEvent = new CalendarEvent();
        calendarEvent.subject = appointment.getSubject() + Synchronizer.SYNCHRONIZER_TAG;
        calendarEvent.location = appointment.getLocation();
        calendarEvent.start = dateFormat(appointment.getStart());
        calendarEvent.end = dateFormat(appointment.getEnd());
        calendarEvent.author = appointment.getOrganizer().getName();
        calendarEvent.modified = dateFormat(appointment.getLastModifiedTime());
        return calendarEvent;
    }

    private static String dateFormat(final Date date) {
        return CalendarEvent.dateFormat.get().format(date);
    }

    private static void fillRepeatedEvents(final Appointment appointment,
        final ExchangeService service, final CalendarEvent calendarEvent,
        final ArrayList<CalendarEvent> out) throws Exception {
        final ItemId id = appointment.getId();
        final Appointment calendarItem = Appointment
            .bind(service, id, new PropertySet(AppointmentSchema.AppointmentType));

        // If the item ID is not for a recurring master, retrieve the recurring master for the series.
        if (calendarItem.getAppointmentType() == AppointmentType.Single) {
            return;
        }
        final GregorianCalendar calendarStart = new GregorianCalendar();
        calendarStart.setTime(appointment.getStart());
        final GregorianCalendar calendarEnd = new GregorianCalendar();
        calendarEnd.setTime(appointment.getEnd());

        int i = 0;
        while (++i < 30) {
            try {
                //System.out.println("Process " + i + " in " + calendarEvent);
                final Appointment appointmentInSeries = Appointment
                    .bindToOccurrence(service, calendarItem.getRootItemId(), i);
                final CalendarEvent eventInSeries = createCalendarEvent(appointmentInSeries);
                if (eventInSeries.isActual()) {
                    out.add(eventInSeries);
                }
            } catch (final ServiceResponseException exception) {
                if (exception.getErrorCode()
                    == ServiceError.ErrorCalendarOccurrenceIndexIsOutOfRecurrenceRange) {
                    // все ок, это потому-что достигли дна
                    return;
                } else if (exception.getErrorCode() == ServiceError.ErrorCalendarOccurrenceIsDeletedFromRecurrence) {
                    // событие было удалено, такое бывает...
                } else {
                    throw new IllegalStateException(exception);
                }
            }
        }
        System.err.println("Too long calendar event! " + calendarEvent);
    }
}
