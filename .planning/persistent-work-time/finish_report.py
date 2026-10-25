import hashlib, json, pathlib, xml.etree.ElementTree as ET

root=pathlib.Path.cwd()
data={}
for directory in ('core/tcp','feature/device'):
    suites=[ET.parse(p).getroot() for p in (root/directory/'build/test-results/testDevDebugUnitTest').glob('TEST-*.xml')]
    data[directory]={key:sum(int(s.attrib[key]) for s in suites) for key in ('tests','failures','errors')}
    assert data[directory]['failures']==0 and data[directory]['errors']==0
data['board_unique_tests']=60
data['board_breakdown']={'independent_counter':16,'execution_start':7,'continuation':10,'runtime_parameters':9,'emergency':18}
data['platform_checks']={'counter_Windows':16,'counter_WSL_Linux':16,'patched_deployed_source_copy':16}
data['remote_actions']='Read-only source fetch; no deployment, history deletion, restart or motion'
data['apk']={}
for abi in ('arm64-v8a','armeabi-v7a'):
    path=root/('app/build/outputs/apk/dev/debug/app-dev-'+abi+'-debug.apk')
    data['apk'][abi]={'path':str(path),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
assert 'BUILD SUCCESSFUL' in (root/'.planning/persistent-work-time/app-verification.log').read_text(errors='replace')
(root/'.planning/persistent-work-time/verification-summary.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(data,ensure_ascii=False))
