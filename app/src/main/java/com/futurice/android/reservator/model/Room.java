package com.futurice.android.reservator.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import com.futurice.android.reservator.common.Helpers;

public class Room implements Serializable {
    // Rooms that are free for no more than this long to future are considered "reserved" (not-bookable)
    static public final int RESERVED_THRESHOLD_MINUTES = 15;
    private static final long serialVersionUID = 1L;
    // Rooms that are free for this long to future are considered "free"
    static private final int FREE_THRESHOLD_MINUTES = 180;
    private String name, email;
    private ArrayList<Reservation> reservations;

    //public Vector<Reservation> getReservations(){
    //	return this.reservations;
    //}
    private int capacity = -1;

    public Room(String name, String email) {
        this.name = name;
        this.email = email;
        this.reservations = new ArrayList<Reservation>();
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public void setReservations(ArrayList<Reservation> reservations) {
        synchronized (this.reservations) {
            this.reservations = reservations;
        }
        Collections.sort(reservations);
    }

    @Override
    public String toString() {
        return name; // + " " + (isFree() ? "(free)" : "(reserved)");
    }

    public boolean isFree() {
        DateTime now = new DateTime();
        return isFreeAt(now);
    }

    public boolean isFreeAt(DateTime time) {
        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                if (r.getStartTime().before(time) && r.getEndTime().after(time)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Reservation getCurrentReservation() {
        DateTime now = new DateTime();

        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                if ( r.getStartTime().before(now) && r.getEndTime().after(now)) {
                    return r;
                }
            }
        }

        return null;
    }

    /**
     * Time in minutes the room is free
     * Precondition: room is free
     *
     * @param from
     * @return Integer.MAX_VALUE if no reservations in future
     */
    public int minutesFreeFrom(DateTime from) {
        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                if (r.getStartTime().after(from)) {
                    return (int) ((r.getStartTime().getTimeInMillis() - from.getTimeInMillis()) / 60000);
                }
            }
        }

        return Integer.MAX_VALUE;
    }

    public Reservation nextReservationAfter(DateTime from) {
        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                if (r.getStartTime().after(from)) {
                   return r;
                }
            }
        }
        return null;
    }

    public Reservation getNextReservationToday(DateTime now, String currentId) {

        DateTime start = new DateTime(now.getTimeInMillis()+1);
        DateTime max = now.add(Calendar.DAY_OF_YEAR, 1).stripTime();

        TimeSpan restOfDay = new TimeSpan(start, max);

        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                if (r.getTimeSpan().intersects(restOfDay)) {
                    if (currentId !=null && currentId == r.getId()) {
                        continue;
                    }
                    return r;
                }
            }
        }
        return null;
    }

    public int minutesFreeFromNow() {
        return minutesFreeFrom(new DateTime());
    }

    /**
     * Time in minutes room is reserved
     * Precondition: room is reserved
     *
     * @param from
     * @return
     */
    public int reservedForFrom(DateTime from) {
        DateTime to = from;

        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                if (r.getStartTime().before(to) && r.getEndTime().after(to)) {
                    to = r.getEndTime();
                    to = to.add(Calendar.MINUTE, 5);
                }
            }
        }

        return (int) (to.getTimeInMillis() - from.getTimeInMillis()) / 60000;
    }

    public int reservedForFromNow() {
        return reservedForFrom(new DateTime());
    }

    /**
     * Prerequisite: isFree
     *
     * @return
     */
    public TimeSpan getNextFreeTime() {
        DateTime now = new DateTime();
        DateTime max = now.add(Calendar.DAY_OF_YEAR, 1).stripTime();


        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                // bound nextFreeTime to
                if (r.getStartTime().after(max)) {
                    return new TimeSpan(now, max);
                }
                if (r.getStartTime().after(now)) {
                    return new TimeSpan(now, r.getStartTime()); //TODO maybe there are many reservations after each other..
                }
            }
        }

        return null;
    }

    /**
     * Prerequisites: None
     *
     * @param length Required slot length in minutes.
     * @return Next free slot today of at least the required length, if any.
     */
    public TimeSpan getNextFreeSlot(int length) {
        DateTime now = new DateTime();
        DateTime max = now.add(Calendar.DAY_OF_YEAR, 1).stripTime();

        TimeSpan candidateSlot = new TimeSpan(now, now.add(Calendar.MINUTE, length));

        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                if (r.getStartTime().afterOrEqual(candidateSlot.getEnd())) return candidateSlot;
                if (r.getTimeSpan().intersects(candidateSlot)) {
                    if (r.getEndTime().afterOrEqual(max)) return null;
                    candidateSlot = new TimeSpan(r.getEndTime(), r.getEndTime().add(Calendar.MINUTE, length));
                }
            }
        }

        return candidateSlot;
    }

    public TimeSpan getNextFreeSlot() {
        return getNextFreeSlot(RESERVED_THRESHOLD_MINUTES);
    }

    public List<Reservation> getReservationsForTimeSpan(TimeSpan ts) {
        List<Reservation> daysReservations = new ArrayList<Reservation>();
        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                if (r.getTimeSpan().intersects(ts)) {
                    daysReservations.add(r);
                }
            }
        }
        return daysReservations;
    }

    @Override
    public int hashCode() {
        return email.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Room) {
            return equals((Room) other);
        }
        return super.equals(other);
    }

    public boolean equals(Room room) {
        return email.equals(room.getEmail());
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Prerequisite: isFree
     *
     * @return true if room is continuously free now and for the rest of the day
     */
    public boolean isFreeRestOfDay() {
        DateTime now = new DateTime();
        DateTime max = now.add(Calendar.DAY_OF_YEAR, 1).stripTime();

        TimeSpan restOfDay = new TimeSpan(now, max);

        synchronized (this.reservations) {
            Iterator iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation r = (Reservation) iterator.next();
                if (r.getTimeSpan().intersects(restOfDay)) return false;
                if (r.getStartTime().after(max)) return true;
            }
        }

        return true;
    }

    public String getStatusText() {
        if (this.isFree()) {
            int freeMinutes = this.minutesFreeFromNow();

            if (freeMinutes > FREE_THRESHOLD_MINUTES) {
                return "Free";
            } else if (freeMinutes < RESERVED_THRESHOLD_MINUTES) {
                return "Reserved";
            } else {
                return "Free for " + Helpers.humanizeTimeSpan(freeMinutes);
            }
        } else {
            return "Reserved";
        }
    }
}
