"""Manual load-test collector command definitions.

The inventory includes Linux host CPU/memory and container network collectors.
The CPU and rate fields of the first vmstat report describe the interval
since boot; distinguish them from subsequent one-second reports.
"""

COLLECTORS = {
    "mpstat": {
        "command": ("mpstat", "-P", "ALL", "1"),
        "filename": "mpstat.log",
        "stamp_all": True,
    },
    "vmstat": {
        "command": ("vmstat", "-n", "1"),
        "filename": "vmstat.log",
        "stamp_all": False,
    },
    "ss": {"kind": "ss", "filename": "ss-samples.jsonl", "privileged": True},
    "nstat": {"kind": "nstat", "filename": "nstat-ts.jsonl", "privileged": True},
    "tcpdump": {"kind": "tcpdump", "filename": "network.pcap", "privileged": True},
}

# Lifecycle collectors are handled at ON/OFF instead of as logging workers.
LIFECYCLE_COLLECTORS = ("jfr", "app-log")
