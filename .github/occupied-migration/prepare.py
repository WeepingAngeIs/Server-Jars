"""Build synthetic stimuli around exact old/candidate jars, never replacement production classes."""
import os,subprocess,shutil
from pathlib import Path
R=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/jail-production/prepare.py'],cwd=R,check=True)
p=R/'dist/packaged-harness/src/main/java/dev/unyielding/bundlesmoke/PlayerOnlySmoke.java'
s=p.read_text(); start=s.index('    static void setup()'); end=s.index('    static void afterAutomatic()')
setup=s[start:end]; setup=setup[:setup.index('        firstDeaths=')]+'''        require(cmd("jailintegrated mode disabled")==1,"Disable admissions failed");
        save();Files.writeString(file("active-player-only"),CertifiedJailSmoke.last.toString());
        pass();
    }
'''
a=setup.index('        // Fixture setup only.'); b=setup.index('        require(cmd("jailcustody init")',a);setup=setup[:a]+setup[b:]
header=s[:s.index('    static void setup()')]
# Keep the existing generic helpers and stimuli; no Cobblemon fixture mutation.
s=header+setup+(R/'.github/occupied-migration/phases.java.txt').read_text()
assert 'com.cobblemon' not in s
p.write_text(s)
