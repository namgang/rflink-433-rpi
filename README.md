# rflink-433-rpi
Utilities for running 433 MHz dirt cheap rf-link devices from a Raspberry-Pi. Especially for the Nexa remote protocol.

One program (rx433) listens and records what a remote control sends when you push a button.
It then analyzes and extracts the message.
A *message* is a command intended to be acted on by a remotely controlled device.

Another program (tx433) can take those messages and transmit them.
