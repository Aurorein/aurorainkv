import { createStore } from 'vuex';

export default createStore({
  state: {
    user: {
      uid: null,
      username: null,
      isLoggedIn: false,
    },
  },
  mutations: {
    SET_USER(state, payload) {
      state.user.uid = payload.uid;
      state.user.username = payload.username;
      state.user.isLoggedIn = true;
    },
    CLEAR_USER(state) {
      state.user.uid = null;
      state.user.username = null;
      state.user.isLoggedIn = false;
    },
  },
  actions: {
    login({ commit }, payload) {
      commit('SET_USER', payload);
    },
    logout({ commit }) {
      commit('CLEAR_USER');
    },
  },
  getters: {
    user: (state) => state.user,
    isLoggedIn: (state) => state.user.isLoggedIn,
  },
});