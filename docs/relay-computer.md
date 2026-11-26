# Relay computer

- `large_relay` (item) → `relay_rack` (8 relays + 1 large iron plate (from the large mechanical press)) → stack **at least 2 racks directly on top of a `relay_computer`**.
- Once formed, the racks light up and the relays click (animated texture). It takes over the nearest formed reactor within 16 blocks:
  - **Fission core**: leases the rod bank target through the core's automatic control lease (the same one the reactor console AUTO mode uses) and moves the rods with `ReactorAutomaticControl` to hold the target share of nominal power. If another controller holds the lease, the status shows "busy".
  - **Fusion reactor**: runs the start-up: vacuum → coil cooling (cryogenics) → magnets → plasma, then ramps the desk setpoint 5% per step to the target. Above 600 K blanket temperature it steps the setpoint down by 10%; on a quench it switches the plasma off. It does not reset an emergency latch.
- Target output: Create value panel on the front (10–100%). Right-click shows the status; goggles show status and target.
- A redstone signal on the computer hands control back to the operator (releases the reactor lease).
