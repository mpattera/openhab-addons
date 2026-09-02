# Ksenia Security Binding

This binding integrates Ksenia security and home automation systems via direct connection.
It provides secure communication with control panels and exposes partitions, zones and other entities.

## Supported Things

- `panel` Represents the alarm control panel.
- `partition` Represents a partition of the alarm system.
- `zone` Represents a zone of the alarm system.

## Auto Discovery

## Binding Configuration

Below are the configuration parameters required to manually define things.

### `panel` Bridge

#### Configuration

| Name | Type | Description | Default | Required |
|------|------|------|------|------|------|
| host | text | Hostname or IP of the Lares 4.0 panel | 192.168.2.97 | yes |
| pin  | text | 6-digit PIN for login | 123456 | yes |

### `partition` Thing

#### Configuration

| Name | Type | Description | Default | Required |
|------|------|------|------|------|------|
| partitionId | text | ID of the partition | N/A | yes |

### `zone` Thing

#### Configuration

| Name | Type | Description | Default | Required |
|------|------|------|------|------|------|
| zoneId   | text | ID of the zone | N/A | yes |

#### Channels

| Channel | Type | Read/Write | Description |
|------|------|------|------|
| armedState               | String | Read       | Current armed state of the partition  |
| alarmState               | String | Read       | Alarm state (OK, AL, AM, etc.)        |
| tamperState              | String | Read       | Tamper state (OK, TAM, TM, etc.)      |
| delaySeconds             | Number | Read       | Entry/exit delay in seconds           |
| zoneState                | String | Read       | Zone state (R, A, FM, T, E)           |
| bypassState              | String | Read       | Bypass status                         |
| tamperMemory             | String | Read       | Tamper memory (C/M/N)                 |
| alarmMemory              | String | Read       | Alarm memory (C/M/N)                  |
| faultMemory              | Switch | Read       | Fault/masking memory                  |

## Full Example

### Thing Configuration

```java
Bridge lares40:bridge:panel1 "Lares Panel" [ host="192.168.2.97", pin="123456" ]

Thing lares40:partition:panel1:partition1 [ partitionId="1" ]
Thing lares40:zone:panel1:zone1 [ zoneId="1" ]
```

### Item Configuration

```java
String Partition1_ArmedState "Armed State" { channel="lares40:partition:panel1:partition1:armedState" }
Switch Zone1_FaultMemory "Zone Fault" { channel="lares40:zone:panel1:zone1:faultMemory" }
```

### Sitemap Configuration

```perl
sitemap lares label="Lares 4.0"
{
    Frame label="Partition" {
        Text item=Partition1_ArmedState
    }
    Frame label="Zone" {
        Switch item=Zone1_FaultMemory
    }
}
```
