<?php
// The portable PHP runner here lacks fileinfo. Use PHP's image MIME detector only in this fixture.
if (!class_exists('finfo')) {
    if (!defined('FILEINFO_MIME_TYPE')) define('FILEINFO_MIME_TYPE',16);
    class finfo {
        public function __construct(int $options) {}
        public function file(string $path): string { return getimagesize($path)['mime']??''; }
    }
}
