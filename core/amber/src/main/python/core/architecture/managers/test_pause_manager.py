import pytest

from core.architecture.managers.pause_manager import PauseManager


class TestPauseManager:
    @pytest.fixture
    def pause_manager(self):
        return PauseManager()

    def test_it_can_init(self, pause_manager):
        pass

    def test_it_is_not_paused_initially(self, pause_manager):
        assert not pause_manager.is_paused()

    def test_it_can_be_paused_and_resumed(self, pause_manager):
        pause_manager.pause()
        assert pause_manager.is_paused()
        pause_manager.resume()
        assert not pause_manager.is_paused()

    def test_it_can_be_paused_when_paused(self, pause_manager):
        pause_manager.pause()
        assert pause_manager.is_paused()
        pause_manager.pause()
        assert pause_manager.is_paused()

    def test_it_can_be_resumed_when_resumed(self, pause_manager):
        pause_manager.pause()
        assert pause_manager.is_paused()
        pause_manager.resume()
        assert not pause_manager.is_paused()
        pause_manager.resume()
        assert not pause_manager.is_paused()
