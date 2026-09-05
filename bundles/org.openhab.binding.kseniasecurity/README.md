# Ksenia Security Binding

This binding integrates Ksenia Security systems.
It provides secure communication with control panels and exposes partitions, zones and other entities.

## Supported Things

- `panel` represents the alarm control panel and is used as a Bridge.
- `partition` represents a partition of the alarm system.
- `zone` represents a zone of the alarm system.

At the moment the binding provides monitoring only.

## Auto Discovery

Automatic discovery is not currently supported. The Bridge reads entities metadata when it connects, but child Things must still be created manually.

## Binding Configuration

Below are the configuration parameters available when defining Things manually.

### `panel` Bridge

#### Configuration

| Name | Required | Description | Default |
| --- | --- | --- | --- |
| host | yes | IP address or hostname of the panel | — |
| port | no | Port used by the panel service | 443 |
| useSecureConnection | no | Use a secure connection | true |
| trustSelfSignedCertificate | no | With a secure connection, accept the self-signed certificate provided by the panel | true |
| reconnectInterval | no | Seconds to wait before attempting to reconnect | 60 |
| responseTimeout | no | Seconds to wait for a command response; increase for slow connections (advanced) | 60 |
| loginType | no | Authentication role (`USER`, `SUPERVISOR`) | SUPERVISOR |
| loginCode | yes | Code for authentication | — |

Only `host` and `loginCode` are required. The other parameters can be omitted from a textual Thing configuration and use the defaults shown above. Main UI displays these defaults as initial values; they can be changed when needed.

#### Channels

| Name | Type | Read/Write | Description |
| --- | --- | --- | --- |
| systemLang | String | Read | Language configured |
| sessionState | String | Read | Programming session state |
| freezeState | String | Read | System freeze state |
| firmwareVersion | String | Read | Firmware version |
| webserverVersion | String | Read | Web server version |
| diagnosticRead | String | Write | Advanced diagnostic command that writes the redacted panel response to the trace log |

For diagnostics, enable TRACE logging for this binding and send a payload type to `diagnosticRead`, for example `OUTPUTS` or `USR_ALL`.
Any payload type is accepted; support is checked by the panel.
The default ID range is `ALL ALL`; to specify a range, send `PAYLOAD_TYPE start end`, for example `ZONES 7 7` or `ZONES 1 10`.
This channel only sends `READ` requests and does not change panel configuration. It does not automatically add a `TYPES` list for `MULTI_TYPES`.
Only PIN fields are currently masked in the trace log; review logs before sharing them because other panel data remains visible.

### `partition` Thing

#### Configuration

| Name | Required | Description | Default |
| --- | --- | --- | --- |
| id | yes | Numeric identifier of the partition | — |

#### Channels

| Name | Type | Read/Write | Description |
| --- | --- | --- | --- |
| description | String | Read | Partition label |
| realtimeState | String | Read | Current state |
| armStatus | Switch | Read | Whether the partition is armed |
| entryDelayStatus | Switch | Read | Whether the entry delay is active |
| exitDelayStatus | Switch | Read | Whether the exit delay is active |
| delayRemaining | Number:Time | Read | Remaining entry or exit delay |
| alarmState | String | Read | Alarm state |
| alarmActive | Switch | Read | Whether an alarm is active |
| alarmMemory | Switch | Read | Whether an alarm is in memory |
| tamperState | String | Read | Tamper state |
| tamperActive | Switch | Read | Whether tamper is active |
| tamperMemory | Switch | Read | Whether tamper is in memory |

### `zone` Thing

#### Configuration

| Name | Required | Description | Default |
| --- | --- | --- | --- |
| id | yes | Numeric identifier of the zone | — |

#### Channels

| Name | Type | Read/Write | Description |
| --- | --- | --- | --- |
| description | String | Read | Zone label |
| realtimeState | String | Read | Current state |
| restStatus | Switch | Read | Whether the zone is at rest |
| alarmStatus | Switch | Read | Whether the zone reports an alarm condition |
| faultStatus | Switch | Read | Whether the zone reports a fault condition |
| tamperStatus | Switch | Read | Whether the zone reports a tamper condition |
| errorStatus | Switch | Read | Whether the zone reports an error condition |
| bypassState | String | Read | Bypass state |
| bypassStatus | Switch | Read | Whether the zone is bypassed |
| tamperState | String | Read | Tamper state |
| tamperActive | Switch | Read | Whether tamper is active |
| tamperMemory | Switch | Read | Whether tamper is in memory |
| alarmState | String | Read | Alarm state |
| alarmActive | Switch | Read | Whether an alarm is active |
| alarmMemory | Switch | Read | Whether an alarm is in memory |
| faultState | String | Read | Fault state |
| faultMemory | Switch | Read | Whether a fault is in memory |

## Full Example

### Thing Configuration

```java
Bridge kseniasecurity:panel:panel1 "Lares Panel" [
    host="192.168.2.97",
    loginCode="A1B2C3"
] {
    Thing partition partition1 "Main Partition" [ id=1 ]
    Thing zone zone1 "Entrance Zone" [ id=3 ]
}
```

### Item Configuration

```java
String Partition1State "Main Partition state" { channel="kseniasecurity:partition:panel1:partition1:realtimeState" }
String Zone1State "Entrance Zone state" { channel="kseniasecurity:zone:panel1:zone1:realtimeState" }
```

### Sitemap Configuration

```perl
sitemap lares label="Lares 4.0" {
    Frame label="Main Partition" {
        Text item=Partition1State
    }
    Frame label="Entrance Zone" {
        Text item=Zone1State
    }
}
```
