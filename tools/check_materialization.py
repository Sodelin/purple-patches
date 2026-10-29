"""Public synthetic controls for committed overlay input and timestamp selection."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from materialize import TOOL_INPUTS, manifest_timestamp_override, snapshot_source


class SourceSnapshotControls(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='purple-materialize-control-')
        self.root=Path(self.temp.name)
        self.env=os.environ.copy()
        self.env.update(GIT_CONFIG_GLOBAL='/dev/null',GIT_CONFIG_NOSYSTEM='1',
                        GIT_AUTHOR_DATE='2026-10-29T01:02:03+00:00',
                        GIT_COMMITTER_DATE='2026-10-29T01:02:03+00:00')
        self.git('-c','init.templateDir=','init','--quiet')
        self.write('.gitignore',b'gboard/*.ignored\n')
        self.write('gboard/src/Test.java',b'class Test {}\n')
        self.write('gboard/PurpleGboardPatch.kt',b'fixture patch\n')
        for name in TOOL_INPUTS:self.write(name,('fixture '+name+'\n').encode())
        self.git('add','--all')
        self.git('-c','user.name=Fixture','-c','user.email=fixture@invalid',
                 '-c','commit.gpgsign=false','-c','core.hooksPath=/dev/null',
                 'commit','--quiet','-m','public fixture')

    def tearDown(self):self.temp.cleanup()

    def git(self,*args):
        return subprocess.check_output(['git','-C',str(self.root),*args],env=self.env,stderr=subprocess.DEVNULL)

    def write(self,name,data):
        target=self.root/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)

    def test_exact_source_and_commit_timestamp(self):
        commit,created,timestamp,contents=snapshot_source(self.root)
        self.assertEqual(commit,self.git('rev-parse','HEAD').decode().strip())
        self.assertEqual(created,'2026-10-29T01:02:03+00:00')
        self.assertEqual(timestamp,1793235723000)
        self.assertEqual(contents['gboard/src/Test.java'],b'class Test {}\n')
        self.assertIn('manifest.attributes["Timestamp"] = "1793235723000"',
                      manifest_timestamp_override(timestamp))
        for invalid in (True,'1',1<<63,-(1<<63)-1):
            with self.assertRaises(ValueError):manifest_timestamp_override(invalid)

    def test_rejects_modified_missing_ignored_and_linked_inputs(self):
        self.write('gboard/src/Test.java',b'changed\n')
        with self.assertRaises(ValueError):snapshot_source(self.root)
        self.write('gboard/src/Test.java',b'class Test {}\n')
        self.write('gboard/injected.ignored',b'ignored but not committed\n')
        self.git('check-ignore','gboard/injected.ignored')
        with self.assertRaises(ValueError):snapshot_source(self.root)
        (self.root/'gboard/injected.ignored').unlink()
        (self.root/'gboard/link').symlink_to('src/Test.java')
        with self.assertRaises(ValueError):snapshot_source(self.root)
        (self.root/'gboard/link').unlink()
        (self.root/'gboard/src/Test.java').unlink()
        with self.assertRaises(ValueError):snapshot_source(self.root)
        self.write('gboard/src/Test.java',b'class Test {}\n')
        self.write('tools/version.txt',b'uncommitted version\n')
        with self.assertRaises(ValueError):snapshot_source(self.root)


if __name__=='__main__':
    unittest.main(verbosity=2)
