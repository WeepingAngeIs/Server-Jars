"""Generate a diagnostic-only variant of the stock admission driver.

Run on the existing disposable hosted stock fixture. The stock driver and its
runtime and JAR checks stay intact. This observes one synthetic player, then
stops after the eligibility check without making a custody transaction.
"""
from pathlib import Path
import shutil
import sys

source = Path(sys.argv[1]).resolve()
destination = Path(sys.argv[2]).resolve()
destination.mkdir(parents=True, exist_ok=True)
driver = (source / "stock_admission.py").read_text(encoding="utf-8")

def replace(old, new):
    global driver
    if driver.count(old) != 1:
        raise RuntimeError("Stock admission driver changed: " + old)
    driver = driver.replace(old, new)

def observe(stage):
    return f'''            srv.stdin.write('say POLYMORPH-BEGIN-{stage}\\n')
            srv.stdin.write('data get entity Visitor neoforge:attachments."polymorph:recipe_data"\\n')
            srv.stdin.write('say POLYMORPH-END-{stage}\\n')
            srv.stdin.flush()
            await_signal(lambda: '[Server] POLYMORPH-END-{stage}' in tail(server_log),
                         [srv,cli], 40, 'Polymorph observation failed')
'''

replace('            # This is an instruction to the unchanged client picker',
        observe('joined') + '            # This is an instruction to the unchanged client picker')
replace('            print("Player completed genuine Human and Warrior onboarding.",flush=True)',
        '            print("Player completed genuine Human and Warrior onboarding.",flush=True)\n' + observe('onboarded').rstrip())
replace('            checked("jailcustody init")', observe('saved') + '            checked("jailcustody init")')
replace('            checked("jailintegrated check Visitor")',
        '''            send("jailintegrated check Visitor")
            send("say POLYMORPH-PREFLIGHT-END")
            await_signal(lambda: "[Server] POLYMORPH-PREFLIGHT-END" in tail(server_log), [srv,cli], 40, "Preflight did not finish")
            print("Polymorph observations and preflight are in native-server.log", flush=True)
            send("stop")
            assert srv.wait(timeout=150)==0
            return''')
(destination / "stock_polymorph_observe.py").write_text(driver,encoding="utf-8")
if source != destination:
    shutil.copy2(source / "rosecourt_pack.py", destination / "rosecourt_pack.py")
