#!/usr/bin/env python3
"""Verify opted-in dummy keys survive two real JOSM processes in one isolated profile."""
import importlib.util,os,pathlib,shutil,signal,subprocess,tempfile,time
ROOT=pathlib.Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('gui_smoke',ROOT/'scripts/gui-smoke.py')
assert spec is not None and spec.loader is not None
helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
def main():
 base=pathlib.Path(tempfile.mkdtemp(prefix='credential-restart-',dir=ROOT/'build'));base.chmod(0o700);home=base/'profile';(home/'plugins').mkdir(parents=True);shutil.copy2(ROOT/'dist/dawarich.jar',home/'plugins/dawarich.jar')
 (home/'preferences.xml').write_text('<preferences xmlns="http://josm.openstreetmap.de/preferences-1.0" version="19678"><list key="plugins"><entry value="dawarich"/></list><tag key="pluginmanager.version" value="19678"/><tag key="pluginmanager.lastupdate" value="'+str(int(time.time()))+'"/><tag key="pluginmanager.check-for-updates" value="false"/></preferences>')
 display=helper.socket.socket()
 for n in range(90,150):
  try:display.bind(('127.0.0.1',6000+n));break
  except OSError:continue
 else:raise RuntimeError('No free X display')
 display.close();auth=base/'Xauthority';auth.write_bytes(helper.authority_record(n,os.urandom(16)));auth.chmod(0o600)
 with (base/'xvfb.log').open('w') as log:
  x=subprocess.Popen(['Xvfb',':'+str(n),'-screen','0','1280x900x24','-listen','tcp','-auth',str(auth)],stdout=log,stderr=subprocess.STDOUT)
  try:
   time.sleep(1);env=dict(os.environ,DISPLAY='127.0.0.1:'+str(n),XAUTHORITY=str(auth))
   for stage in ['seed','restore']:
    with (base/(stage+'.log')).open('w') as output:
     p=subprocess.Popen([helper.JAVA,'-Djosm.home='+str(home),'-Dsmoke.stage='+stage,'-cp',str(helper.JOSM),str(ROOT/'test/gui/SavedKeyRestartSmoke.java')],cwd=base,env=env,stdout=output,stderr=subprocess.STDOUT,start_new_session=True)
     try:code=p.wait(timeout=45)
     except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGTERM);p.wait(timeout=5);code=124
    text=(base/(stage+'.log')).read_text();print('\n'.join(line for line in text.splitlines() if line.startswith('SMOKE_')))
    if code:print(text[-4500:]);raise RuntimeError('Credential restart stage failed: '+stage)
    if stage=='seed':shutil.copy2(ROOT/'dist/dawarich.jar',home/'plugins/dawarich.jar')
  finally:x.terminate();x.wait(timeout=5);auth.unlink(missing_ok=True)
 print('Synthetic-only credential restart receipts:',base.relative_to(ROOT))
if __name__=='__main__':main()
