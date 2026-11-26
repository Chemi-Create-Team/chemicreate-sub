# Relay computer

- `large_relay` (item) → `relay_rack` (8 relays + 1 large iron plate (from the large mechanical press)) → stack **at least 2 racks directly on top of a `relay_computer`**.
- Once formed, the racks light up and the relays click (animated texture). It takes over the nearest formed reactor within 16 blocks:
  - **Fission core**: leases the rod bank target through the core's automatic control lease (the same one the reactor console AUTO mode uses) and moves the rods with the relay program (the same rules as the console's automatic control, in integers) to hold the target share of nominal power. If another controller holds the lease, the status shows "busy".
  - **Fusion reactor**: runs the start-up: vacuum → coil cooling (cryogenics) → magnets → plasma, then ramps the desk setpoint 5% per step to the target. Above 600 K blanket temperature it steps the setpoint down by 10%; on a quench it switches the plasma off. It does not reset an emergency latch.
- Target output: Create value panel on the front (10–100%). Right-click shows the status; goggles show status and target.
- A redstone signal on the computer hands control back to the operator (releases the reactor lease).

## How it computes
- The computer really calculates with relays. `RelayAlu` simulates a 12-bit relay adder/subtractor relay by relay: per bit there are input relays A and B, a carry relay and a sum relay, plus one SUB relay. Each relay's contacts follow its coil one step later, so carries ripple through the bank.
- Sensors come in as scaled integers through meter contacts. Every sum, difference and comparison in the control programs (`RelayPrograms.fission` and `RelayPrograms.fusion`) runs on that bank. Thresholds are wired constants, and the ×1/4 is a two-bit shift (wiring only).
- A pass takes as long as its relay steps: 8 steps per tick per rack, up to 8 racks. The pass waits at least 9 ticks. While the relays work, the fission rod lease is renewed with the last result.
- Every relay that switches makes a click (`relay.click`, three generated variants from `tools/generate_relay_clicks.py`). The volume follows how many relays switched.
