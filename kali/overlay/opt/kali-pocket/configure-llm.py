import getpass,json,secrets,os
from pathlib import Path
p=Path('/root/.config/kali-pocket.json')
config={'token':secrets.token_urlsafe(32),'llm_url':input('LLM base URL (include /v1): ').strip(),'model':input('Model: ').strip(),'api_key':getpass.getpass('API key (blank if none): '),'lan':input('Allow LAN clients? [y/N]: ').lower()=='y'}
p.parent.mkdir(parents=True,exist_ok=True)
fd=os.open(str(p),os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,'w') as f: json.dump(config,f)
print('Your bridge token:',config['token'])
print('Start with: python3 /opt/kali-pocket/llm-bridge.py --config /root/.config/kali-pocket.json')
