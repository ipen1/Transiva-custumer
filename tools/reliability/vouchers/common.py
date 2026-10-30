import os,json,subprocess

def php_command():
    extra=json.loads(os.environ.get('VOUCHER_PHP_ARGS','[]'))
    if not isinstance(extra,list) or not all(isinstance(x,str) for x in extra):
        raise ValueError('VOUCHER_PHP_ARGS must be a JSON array of arguments')
    return [os.environ.get('PHP_BIN','php'),*extra,'-d','date.timezone=UTC']

def stop_process(process):
    if process.poll() is not None:return
    process.terminate()
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill();process.wait(timeout=5)
