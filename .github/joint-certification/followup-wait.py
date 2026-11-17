"""Wait only for the three pinned wallet slices, never accept a failed slice."""
import json
import os
import time
import urllib.request

jobs = {133796208803: 'core', 133796208906: 'faults', 133796208796: 'integrated'}
deadline = time.monotonic() + 1200
while jobs:
    for job_id in list(jobs):
        request = urllib.request.Request(f'https://api.github.com/repos/WeepingAngeIs/Server-Jars/actions/jobs/{job_id}',
                                         headers={'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
                                                  'Accept': 'application/vnd.github+json'})
        with urllib.request.urlopen(request, timeout=30) as response:
            job = json.load(response)
        assert job['run_id'] == 44010514867
        if job['status'] == 'completed':
            assert job['conclusion'] == 'success', (jobs[job_id], job['conclusion'])
            print('Verified completed wallet slice:', jobs.pop(job_id))
    assert time.monotonic() < deadline, 'Pinned wallet evidence did not complete within the wait limit'
    if jobs:
        time.sleep(20)
