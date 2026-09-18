let fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
let {spawnSync} = require('node:child_process');
let root = path.resolve(__dirname, '../../..');
let video = path.join(root, 'models/promised_consort/.tools/reference-video/BV1tyeyenEBB_p1.mp4');
let metadata = JSON.parse(fs.readFileSync(video.replace('.mp4', '.json'), 'utf8'));
assert.equal(fs.statSync(video).size, metadata.bytes, 'Cached reference changed');
let output = path.join(root, 'build/ai-previews/movement-feedback-reference.jpg');
fs.mkdirSync(path.dirname(output), {recursive: true});
assert(!fs.existsSync(output), 'Remove the old temporary review before recapturing');
let label = "drawtext=fontfile='C\\:/Windows/Fonts/consola.ttf':text='%{pts\\:hms}':fontcolor=white:fontsize=16:box=1:boxcolor=black@0.8:x=4:y=h-th-4";
let filter = '[0:v]split=2[meteor][lion];'
    + '[meteor]trim=start=150:end=158,fps=5,scale=400:225,' + label + ',tile=5x8:nb_frames=40,setpts=PTS-STARTPTS[first];'
    + '[lion]trim=start=162:end=170,fps=5,scale=400:225,' + label + ',tile=5x8:nb_frames=40,setpts=PTS-STARTPTS[second];'
    + '[first][second]hstack=inputs=2[out]';
let result = spawnSync(path.join(root, 'models/malenia/.tools/node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe'),
    ['-hide_banner', '-loglevel', 'error', '-i', video, '-filter_complex', filter, '-map', '[out]', '-frames:v', '1', '-q:v', '6', output],
    {encoding: 'utf8', maxBuffer: 1024 * 1024});
assert.equal(result.status, 0, result.stderr);
let bytes = fs.statSync(output).size;
assert(bytes < 1048576, 'Make a smaller preview before image inspection');
console.log(JSON.stringify({bytes, samples: 80, left_seconds: [150, 158], right_seconds: [162, 170], sampling_fps: 5,
    labels: 'absolute source time; left40 meteor, right40 lion; sampled frames, not every native frame', inspected: false}));