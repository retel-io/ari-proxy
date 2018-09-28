all: deb mv_build

deb: inject_maven_settings
	@echo '[INFO] Running dpkg-buildpackage...'
	dpkg-buildpackage -uc -us -rfakeroot
	@echo '[INFO] Done.'

mv_build: deb
	@echo '[INFO] Moving deb to build folder...'
	mkdir -p build/
	mv ../ari-proxy_*.deb build/
	rm -f ../ari-proxy_*
	@echo '[INFO] Done.'

inject_maven_settings:
	@echo '[INFO] Injecting maven settings...'
	mkdir -p $(PWD)/.m2/
	cp $(PWD)/maven_settings.xml $(PWD)/.m2/settings.xml
	@echo '[INFO] Done.'

clean:
	@echo '[INFO] Cleaning up dpkg build...'
	rm -rf $(PWD)/debian/ari-proxy*
	rm -f $(PWD)/debian/debhelper-build-stamp
	rm -rf $(PWD)/.m2
	rm -rf $(PWD)/build
	@echo '[INFO] Done.'
