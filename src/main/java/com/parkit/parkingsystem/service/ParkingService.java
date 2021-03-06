package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;

public class ParkingService {

    private static final int IS_CAR = 1;
    private static final int IS_BIKE = 2;

    private static final int REDUCE_PERCENT = 5;

    private static final Logger LOGGER = LogManager.getLogger("ParkingService");

    private static FareCalculatorService fareCalculatorService =
            new FareCalculatorService();

    private InputReaderUtil inputReaderUtil;
    private ParkingSpotDAO parkingSpotDAO;
    private TicketDAO ticketDAO;

    public ParkingService(final InputReaderUtil psInputReaderUtil,
                          final ParkingSpotDAO psParkingSpotDAO,
                          final TicketDAO psTicketDAO) {
        this.inputReaderUtil = psInputReaderUtil;
        this.parkingSpotDAO = psParkingSpotDAO;
        this.ticketDAO = psTicketDAO;
    }

    public void processIncomingVehicle() {
        processIncomingVehicle(new Date());
    }

    public void processIncomingVehicle(final Date inTime) {
        try {
            ParkingSpot parkingSpot = getNextParkingNumberIfAvailable();
            if (parkingSpot != null && parkingSpot.getId() > 0) {
                String vehicleRegNumber = getVehichleRegNumber();
                parkingSpot.setAvailable(false);
                parkingSpotDAO.updateParking(parkingSpot);
                //allot this parking space and mark it's availability as false
                Ticket ticket = new Ticket();
                //ID, PARKING_NUMBER, VEHICLE_REG_NUMBER,
                // PRICE, IN_TIME, OUT_TIME, IS_PAID)
                //ticket.setId(ticketID);
                ticket.setParkingSpot(parkingSpot);
                ticket.setVehicleRegNumber(vehicleRegNumber);
                ticket.setPrice(0);
                ticket.setInTime(inTime);
                ticket.setOutTime(null);
                ticket.setPaid(false);

                ticketDAO.saveTicket(ticket);
                System.out.println("Generated Ticket and saved in DB");
                System.out.println("Please park your vehicle in spot number:"
                        + parkingSpot.getId());
                System.out.println("Recorded in-time for vehicle number:"
                        + vehicleRegNumber + " is:" + inTime);
            }
        } catch (Exception e) {
            LOGGER.error("Unable to process incoming vehicle", e);
        }
    }

    private String getVehichleRegNumber() throws Exception {
        System.out.println("Please type the vehicle"
                + " registration number and press enter key");
        return inputReaderUtil.readVehicleRegistrationNumber();
    }

    public ParkingSpot getNextParkingNumberIfAvailable() {
        ParkingSpot parkingSpot = null;
        try {
            ParkingType parkingType = getVehichleType();
            int parkingNb = parkingSpotDAO.getNextAvailableSlot(parkingType);
            if (parkingNb > 0) {
                parkingSpot = new ParkingSpot(parkingNb, parkingType, true);
            } else {
                throw new Exception("Error fetching parking number from DB."
                        + " Parking slots might be full");
            }
        } catch (IllegalArgumentException ie) {
            LOGGER.error("Error parsing user input for type of vehicle", ie);
        } catch (Exception e) {
            LOGGER.error("Error fetching next available parking slot", e);
        }
        return parkingSpot;
    }

    private ParkingType getVehichleType() {
        System.out.println("Please select vehicle type from menu");
        System.out.println("1 CAR");
        System.out.println("2 BIKE");
        int input = inputReaderUtil.readSelection();
        switch (input) {
            case IS_CAR: {
                return ParkingType.CAR;
            }
            case IS_BIKE: {
                return ParkingType.BIKE;
            }
            default: {
                System.out.println("Incorrect input provided");
                throw new IllegalArgumentException("Entered input is invalid");
            }
        }
    }

    public void processExitingVehicle() {
        try {
            String vehicleRegNumber = getVehichleRegNumber();
            Ticket ticket = ticketDAO.getTicket(vehicleRegNumber);
            Date outTime = new Date();
            ticket.setOutTime(outTime);
            if (ticketDAO.isRecurrentUser(ticket.getVehicleRegNumber(), 2)) {
                fareCalculatorService.calculateFareReduce(
                        ticket, REDUCE_PERCENT);
            } else {
                fareCalculatorService.calculateFare(ticket);
            }
            if (ticketDAO.updateTicket(ticket)) {
                ParkingSpot parkingSpot = ticket.getParkingSpot();
                parkingSpot.setAvailable(true);
                parkingSpotDAO.updateParking(parkingSpot);
                System.out.println("Please pay the parking fare:"
                        + ticket.getPrice());
                System.out.println("Recorded out-time for vehicle number:"
                        + ticket.getVehicleRegNumber() + " is:" + outTime);
            } else {
                System.out.println("Unable to update ticket information."
                        + " Error occurred");
            }
        } catch (Exception e) {
            LOGGER.error("Unable to process exiting vehicle", e);
        }
    }
}
