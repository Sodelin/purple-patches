"""Fetch one exact public APKMirror input OFF PHONE; verify full published hash."""
from html import unescape
from html.parser import HTMLParser
import hashlib
import os
from pathlib import Path
import sys
from urllib.parse import urljoin, urlparse
from curl_cffi import requests

PAGE = 'https://www.apkmirror.com/apk/google-inc/gboard/gboard-the-google-keyboard-18-5-4-993608717-release/gboard-the-google-keyboard-18-5-4-993608717-release-arm64-v8a-3-android-apk-download/'
EXPECTED_BYTES = 80701877
EXPECTED_SHA256 = '0bbc448e570f4042692c850f8b5a19458e009d245c5f099bff3c0415574c2402'

class Links(HTMLParser):
    def __init__(self):
        super().__init__(); self.links=[]
    def handle_starttag(self, tag, attrs):
        if tag == 'a':
            values=dict(attrs)
            if values.get('href'): self.links.append(unescape(values['href']))


def allowed(url):
    value=urlparse(url)
    return value.scheme=='https' and (value.hostname=='apkmirror.com' or (value.hostname or '').endswith('.apkmirror.com'))


def fetch(destination):
    session=requests.Session(impersonate='chrome')
    url=PAGE
    for _ in range(4):
        if not allowed(url): raise RuntimeError('Unexpected download host')
        response=session.get(url, timeout=60, allow_redirects=True, stream=True)
        response.raise_for_status()
        if not allowed(response.url): raise RuntimeError('Unexpected redirect host')
        kind=response.headers.get('Content-Type','').lower()
        if 'text/html' not in kind:
            digest=hashlib.sha256();count=0
            temporary=destination.with_suffix('.part')
            with temporary.open('xb') as output:
                for chunk in response.iter_content(chunk_size=1024*1024):
                    count+=len(chunk)
                    if count>EXPECTED_BYTES: raise RuntimeError('APK differs from published size')
                    digest.update(chunk);output.write(chunk)
            if count!=EXPECTED_BYTES or digest.hexdigest()!=EXPECTED_SHA256:
                temporary.unlink();raise RuntimeError('APK differs from published full hash')
            os.replace(temporary,destination)
            print('Exact supported APKMirror release input downloaded and SHA256 verified')
            return
        body=b''.join(response.iter_content())
        parser=Links();parser.feed(body.decode('utf-8','replace'))
        choices=[urljoin(response.url,link) for link in parser.links if 'download.php?' in link]
        if not choices:
            choices=[urljoin(response.url,link) for link in parser.links if '/download/' in link and 'key=' in link]
        if not choices: raise RuntimeError('No ordinary download link returned by APKMirror')
        url=choices[0]
    raise RuntimeError('APKMirror did not return the APK within the download flow')

if __name__=='__main__':
    fetch(Path(sys.argv[1]))
